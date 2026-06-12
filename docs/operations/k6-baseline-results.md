# k6 부하 테스트 — 시나리오별 설명 및 베이스라인 결과

> **목적**: 튜닝 전 SLO 기준선 수치 확정 및 도메인 오너별 최적화 피드백 전달  
> **실행 환경**: EC2 t3.small (단일 인스턴스) — Spring Boot + Prometheus + Grafana + k6 동시 실행  
> **실행일**: 2026-06-12  
> **기준 SLO**: `docs/observability-metrics.md` 참고

---

## 테스트 환경 공통 사항

| 항목 | 값 |
|---|---|
| EC2 인스턴스 | t3.small (2 vCPU, 2 GB RAM) |
| Spring Boot | Blue/Green 슬롯 중 활성 슬롯 직접 접근 (`:8081` 또는 `:8082`) |
| DB | RDS MySQL (별도 인스턴스) |
| Redis | ElastiCache (별도 인스턴스) |
| 모니터링 | Prometheus + Grafana (동일 EC2) |
| 토큰 | `infra/k6/seed/tokens.csv` — fan_id 1~2100 JWT |

> **주의**: k6가 Spring Boot + 모니터링 스택과 같은 EC2에서 실행되므로 CPU/메모리 경합이 발생합니다.  
> tail latency(P90~P95)가 실제 서비스 환경보다 높게 측정될 수 있습니다.  
> **절대 수치보다 최적화 전/후 동일 환경 비교값이 중요합니다.** 최적화 후 반드시 동일 조건에서 재측정하여 개선폭을 확인하세요.

---

## 공통 실행 명령어 패턴

```bash
# EC2에서 활성 슬롯 자동 감지 후 실행 (모든 시나리오 공통)
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT --out experimental-prometheus-rw scenarios/<번호>_<이름>.js
```

---

## SLO 목표 요약

| 유형 | P95 목표 | 에러율 목표 | 적용 시나리오 |
|---|---|---|---|
| Read | < 120ms | < 0.1% | 02 |
| Write | < 300ms | < 0.1% | 01, 04, 06 |
| Payment | < 3,000ms | < 1% | 03 |
| SSE | 연결 거부 없음 (정상 구간) | — | 05 |

---

## 시나리오 01: 주문 동시성 (Order Concurrency)

**파일**: `infra/k6/scenarios/01_order_concurrency.js`  
**담당 오너**: 형성빈  
**SLO**: `orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 목적

200 VU가 동시에 `POST /api/v1/orders`를 호출할 때 재고 100개에 대해 오버셀이 발생하지 않는지 검증한다.  
Redis 기반 분산 락 + 대기열 처리의 동시성 정확성을 확인하는 핵심 시나리오.

### 실행 흐름

1. **iteration 1**: 대기열 진입 (`queue/join`) + accessToken 획득 — 200 VU가 단일 스케줄러 배치로 처리
2. **iteration 2**: 200 VU 동시 `POST /orders` 발화 → 재고 100개 → 100건 RESERVED, 100건 409 DEPLETED 기대

### 사전 준비

```bash
# DB: product_id=1 재고 100개 초기화
UPDATE inventory SET available_qty=100, reserved_qty=0, version=0 WHERE product_id=1;

# Redis: access ticket 일괄 적재 (fan_id 1~2100)
for i in $(seq 1 2100); do
  redis-cli SET "access:ticket:1:$i" "test-ticket-token" EX 3600
done

# 서버 설정 확인
fandrops.queue.max-concurrent-processing=300
fandrops.queue.advance-batch-size=300
fandrops.queue.scheduler.interval-ms=1000
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  -e FAN_POOL_SIZE=200 \
  --out experimental-prometheus-rw \
  scenarios/01_order_concurrency.js
```

### 결과

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | — | < 300ms | 미실행 |
| 에러율 | — | < 0.1% | 미실행 |
| orders_reserved | — | ≤ 100 | 미실행 |

> 실행 후 `SELECT reserved_qty FROM inventory WHERE product_id=1;`로 오버셀 수동 검증 필요.

### 오너 피드백 (형성빈)

_미실행 — 결과 기록 후 업데이트 예정_

---

## 시나리오 02: 피드 조회 Read P95 기준선

**파일**: `infra/k6/scenarios/02_feed_read.js`  
**담당 오너**: 정환철  
**SLO**: P95 < 120ms, 에러율 < 0.1%

### 목적

`GET /api/v1/artists/{id}/feeds` 엔드포인트의 읽기 성능 기준선을 측정한다.  
50 VU 2분 constant-vus 부하에서 P95 응답 시간이 120ms 이내인지 확인한다.

### 실행 흐름

- 50 VU가 2분간 `/api/v1/artists/1/feeds` 지속 호출
- VU별 JWT 토큰은 `tokens.csv`에서 순환 분배
- 응답에 `data.items` 배열 포함 여부 체크

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  --out experimental-prometheus-rw \
  scenarios/02_feed_read.js
```

### 결과 (2026-06-12, 3회차)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | **287.67ms** | < 120ms | ❌ SLO 미달 |
| P90 응답 시간 | 239.91ms | — | — |
| 평균 응답 시간 | 155.37ms | — | — |
| 최소 응답 시간 | 5.52ms | — | — |
| 최대 응답 시간 | 900.68ms | — | — |
| 에러율 | 0.00% | < 0.1% | ✅ |
| 처리량 | 320.9 req/s | — | — |
| 총 요청 수 | 38,553 | — | — |

<details>
<summary>측정 이력</summary>

| 회차 | 날짜 | P95 | 비고 |
|---|---|---|---|
| 1회 | 2026-06-12 | 206ms | 모니터링 스택 추가 전 |
| 2회 | 2026-06-12 | 291ms | 모니터링 스택 동시 실행 |
| 3회 | 2026-06-12 | 287.67ms | Prometheus 스크레이프 수정 후 공식 기록 |

</details>

### 오너 피드백 (정환철)

**현상**: P95 287ms로 목표(120ms) 대비 **2.4배 초과**.  
min=5.52ms이므로 엔드포인트 자체는 빠르게 응답 가능. 50 VU 부하 하에서 tail latency가 급격히 올라가는 패턴은 일반적으로 **DB 쿼리 비효율** 또는 **커넥션 풀 대기**에서 기인한다.

**확인 요청 사항:**

1. **N+1 쿼리 여부**: 피드 목록 조회 시 artist_profile, artist_feed, user_follow 등 연관 엔티티를 별도 쿼리로 조회하는지 확인. `spring.jpa.show-sql=true`로 쿼리 로그 확인 권장.
2. **인덱스 유무**: `artist_feed.artist_id` 컬럼에 인덱스가 있는지 `EXPLAIN` 실행으로 확인.
3. **커서 페이지네이션 쿼리**: 커서 기반 페이지네이션에서 Full Scan이 발생하지 않는지 확인.
4. **캐싱 검토**: 피드 목록은 읽기 빈도가 높으므로 Redis 캐싱(TTL 30~60s) 도입 검토.

> **참고**: t3.small 단일 인스턴스(k6 + 모니터링 동시 실행) 환경으로 절대 수치는 부풀려질 수 있음. 쿼리 최적화 후 **동일 환경에서 재측정**하여 개선폭을 비교하는 것이 목표.

---

## 시나리오 03: 결제 확인 (Payment Confirm)

**파일**: `infra/k6/scenarios/03_payment_confirm.js`  
**담당 오너**: 장성재  
**SLO**: P95 < 3,000ms, 에러율 < 1%

### 목적

`POST /api/v1/payments/toss/confirm` 엔드포인트에 대해 Wiremock으로 Toss PG를 모킹하여 다양한 응답 시나리오(성공/타임아웃/잔액부족/서버오류)를 부하 하에서 검증한다.

### 실행 흐름

- 0 → 50 VU 30초 램프업 → 50 VU 2분 유지 → 0 VU 10초 램프다운
- `SCENARIO` 환경변수로 Wiremock stub 라우팅 제어

| SCENARIO | Wiremock 응답 | 기대 결과 |
|---|---|---|
| `success` (기본) | 200 즉시 | 정상 처리 |
| `timeout` | 200, 5초 지연 | P95 < 3s 내 처리 |
| `balance-error` | 400 잔액부족 | 400 정상 반환 |
| `server-error` | 500 PG 오류 | 5xx 에러율 < 1% |
| `mixed` | 70/10/10/10% 혼합 | 전체 에러율 < 1% |

### 사전 준비

```bash
# 1. Wiremock 기동
docker run -d --name wiremock -p 8090:8080 \
  -v $(pwd)/infra/k6/wiremock/mappings:/home/wiremock/mappings \
  wiremock/wiremock:3.3.1 --global-response-templating

# 2. 앱 서버 환경변수 설정 후 재기동
TOSS_API_BASE_URL=http://localhost:8090

# 3. DB: RESERVED 상태 주문 seed (fan_id=1~50, product_id=1, amount=15000)
# seed/orders.json 파일 준비
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  --out experimental-prometheus-rw \
  scenarios/03_payment_confirm.js
```

### 결과

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | — | < 3,000ms | 미실행 |
| 에러율 | — | < 1% | 미실행 |

### 오너 피드백 (장성재)

_미실행 — 결과 기록 후 업데이트 예정_

---

## 시나리오 04: 드롭스 스파이크 (Drop Spike)

**파일**: `infra/k6/scenarios/04_drop_spike.js`  
**담당 오너**: 형성빈  
**SLO**: `spike_orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 목적

드롭스 상품 오픈 시 발생하는 급격한 트래픽 스파이크(0 → 1,000 VU, 30초)를 시뮬레이션하여 재고 오버셀 없이 처리 가능한지 검증한다.

### 실행 흐름

| 단계 | VU | 시간 | 설명 |
|---|---|---|---|
| 급상승 | 0 → 1,000 | 30s | 드롭스 오픈 순간 모사 |
| 유지 | 1,000 | 30s | 최고 부하 유지 |
| 종료 | 1,000 → 0 | 15s | 부하 해제 |

### 사전 준비

```bash
# 시나리오 01 실행 후 반드시 재초기화 필요 (티켓 invalidate됨)
UPDATE inventory SET available_qty=100, reserved_qty=0, version=0 WHERE product_id=1;

# Redis: access ticket 재적재 (fan_id 1~2100)
for i in $(seq 1 2100); do
  redis-cli SET "access:ticket:1:$i" "test-ticket-token" EX 3600
done
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  --out experimental-prometheus-rw \
  scenarios/04_drop_spike.js
```

### 결과

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | — | < 300ms | 미실행 |
| 에러율 | — | < 0.1% | 미실행 |
| spike_orders_reserved | — | ≤ 100 | 미실행 |

> 실행 후 `SELECT reserved_qty FROM inventory WHERE product_id=1;`로 오버셀 수동 검증 필요.

### 오너 피드백 (형성빈)

_미실행 — 결과 기록 후 업데이트 예정_

---

## 시나리오 05: SSE 대기열 연결 안정성 (SSE Queue)

**파일**: `infra/k6/scenarios/05_sse_queue.js`  
**담당 오너**: 장성재, 지영재  
**SLO**: 정상 구간 에러율 < 0.1%, 경계 구간 에러율 < 1%, 2,100 VU 초과 시 429 응답 필수

### 목적

SSE 대기열 엔드포인트(`GET /api/v1/queue/stream/{productId}`)의 연결 수 한계를 검증한다.  
Nginx worker_connections 및 JVM FD 한계 내에서 안정적으로 동작하는지 확인하고, 2,100 VU 초과 시 429(`retryable:true`) 응답 계약을 검증한다.

### 실행 흐름

| 단계 | VU | 시작 시간 | 설명 |
|---|---|---|---|
| 정상 부하 | 0 → 1,000 → 0 | 0s | P95 연결 지연·메모리 기준선 |
| 경계 | 0 → 1,800 → 0 | 2m | 상한 직전 안정성 검증 |
| 초과 | 0 → 2,100 → 0 | 4m30s | 429 + `retryable:true` 계약 검증 |

### 사전 준비

```bash
# Nginx worker_connections 확인
grep worker_connections /etc/nginx/nginx.conf  # ≥ 2048 필요

# JVM FD 한계 확인
ulimit -n  # ≥ 8192 필요
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  --out experimental-prometheus-rw \
  scenarios/05_sse_queue.js
```

### 결과

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| 정상 구간 에러율 | — | < 0.1% | 미실행 |
| 경계 구간 에러율 | — | < 1% | 미실행 |
| 초과 구간 429 발생 | — | count > 0 | 미실행 |
| 429 retryable:true | — | 필수 | 미실행 |

### 오너 피드백 (장성재, 지영재)

_미실행 — 결과 기록 후 업데이트 예정_

---

## 시나리오 06: 통합 워크로드 모델 (Workload Model)

**파일**: `infra/k6/scenarios/06_workload_model.js`  
**담당 오너**: 전체  
**SLO**: P95 < 300ms (혼합 전체 기준), 에러율 < 0.1%

### 목적

실제 서비스 트래픽 패턴을 단일 스크립트로 재현하여 혼합 부하 하에서 전체 SLO를 측정한다.

### 워크로드 분포

| 엔드포인트 | 비율 | 담당 |
|---|---|---|
| `GET /api/v1/artists/{id}/feeds` | 60% | 정환철 |
| `POST /api/v1/queue/join/{productId}` | 20% | 장성재, 지영재 |
| `POST /api/v1/orders` | 15% | 형성빈 |
| `POST /api/v1/payments/toss/confirm` | 5% | 장성재 |

### 실행 흐름

| 단계 | VU | 시간 | 설명 |
|---|---|---|---|
| 워밍업 | 0 → 50 | 1m | 캐시·커넥션 풀 준비 |
| 정상 부하 | 50 → 100 | 3m | 기준 측정 구간 |
| 스파이크 | 100 → 150 | 1m | 순간 부하 |
| 쿨다운 | 150 → 0 | 30s | 종료 |

### 사전 준비

```bash
# 시나리오 01·04 완료 후 재고 리셋
UPDATE inventory SET available_qty=200, reserved_qty=0, version=0 WHERE product_id=1;

# Redis: access ticket 재적재
for i in $(seq 1 2100); do
  redis-cli SET "access:ticket:1:$i" "test-ticket-token" EX 3600
done

# Wiremock 기동 (결제 5% 구간 필요)
docker run -d --name wiremock -p 8090:8080 \
  -v $(pwd)/infra/k6/wiremock/mappings:/home/wiremock/mappings \
  wiremock/wiremock:3.3.1 --global-response-templating
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
k6 run -e BASE_URL=http://localhost:$PORT \
  -e PRODUCT_ID=1 \
  -e ARTIST_ID=1 \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  --out experimental-prometheus-rw \
  scenarios/06_workload_model.js
```

### 결과

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 (혼합 전체) | — | < 300ms | 미실행 |
| 에러율 | — | < 0.1% | 미실행 |
| wl_feed 요청 수 | — | > 0 | 미실행 |
| wl_queue 요청 수 | — | > 0 | 미실행 |
| wl_order 요청 수 | — | > 0 | 미실행 |
| wl_payment 요청 수 | — | > 0 | 미실행 |

### 오너 피드백 (전체)

_미실행 — 결과 기록 후 업데이트 예정_

---

## 전체 결과 요약

| 시나리오 | 담당 | P95 | 에러율 | 오버셀 | 상태 |
|---|---|---|---|---|---|
| 01 주문 동시성 | 형성빈 | — | — | — | 미실행 |
| 02 피드 조회 | 정환철 | 287.67ms | 0.00% | — | ❌ SLO 미달 |
| 03 결제 확인 | 장성재 | — | — | — | 미실행 |
| 04 드롭스 스파이크 | 형성빈 | — | — | — | 미실행 |
| 05 SSE 대기열 | 장성재, 지영재 | — | — | — | 미실행 |
| 06 통합 워크로드 | 전체 | — | — | — | 미실행 |

---

## 최적화 우선순위 (실행 완료 후 업데이트 예정)

| 우선순위 | 시나리오 | 도메인 | 개선 방향 |
|---|---|---|---|
| P0 | 02 피드 조회 | community | N+1 쿼리 제거, 인덱스 확인, Redis 캐싱 |