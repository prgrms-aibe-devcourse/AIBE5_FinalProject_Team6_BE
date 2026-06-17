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

## k6 수치 vs Grafana 수치 차이

같은 테스트에서 두 수치가 다르게 나오는 것은 정상입니다.

| 구분 | 측정 주체 | 측정 방식 | 특징 |
|---|---|---|---|
| **k6** | 클라이언트(로드 테스터) | 테스트 전체 기간의 정확한 집계 | 공식 베이스라인으로 사용 |
| **Grafana(Micrometer)** | 서버(Spring Boot 내부) | 1분 슬라이딩 윈도우 `histogram_quantile` 근사값 | 실시간 추세 모니터링용 |

**시나리오 02 예시:**
- k6 P95 = **287ms** — 2분간 38,553건 전체 집계
- Grafana P95 최고점 = **381ms** — 가장 나쁜 1분 구간의 근사값 (JVM GC·워밍업 구간 포함 가능)

> 발표/포트폴리오에서는 **k6 수치를 공식 기준**으로 사용하고, Grafana 수치는 "서버 내부 순간 최고점"으로 구분해서 설명하면 됩니다. 두 수치가 다른 이유를 설명할 수 있으면 오히려 모니터링 구조를 이해하고 있다는 증거입니다.

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
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)" k6 run \
  -e BASE_URL=http://localhost:$PORT \
  -e FAN_POOL_SIZE=200 \
  --out experimental-prometheus-rw \
  scenarios/01_order_concurrency.js
```

> `K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"` — Prometheus에 p95/p99 게이지 메트릭 기록. 미설정 시 p99만 내보냄.

### 결과 (2026-06-15, 1회차 — 참고용)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | **4.43s** | < 300ms | ❌ SLO 미달 |
| P90 응답 시간 | 4.07s | — | — |
| 평균 응답 시간 | 2.96s | — | — |
| 에러율 | **62.50%** | < 0.1% | ❌ |
| orders_reserved | **150** | ≤ 100 | ❌ **오버셀 50건** |
| 총 요청 수 | 400 (200 VU × 2 iter) | — | — |
| 처리량 | 55.2 req/s | — | — |

**DB 사후 검증:**
```
inventory: available_qty=0, reserved_qty=100, total_qty=100
orders:    RESERVED=150, CANCELLED=50
```
→ inventory는 100 정상이나 orders에 150건 RESERVED — 주문 생성과 재고 차감 시점 불일치로 동시 150건이 201 통과.

### 결과 (2026-06-15, 2회차 — **공식 베이스라인**)

> 1회차 이후 inventory reset + Redis 재적재 후 재실행. `K6_PROMETHEUS_RW_TREND_STATS` 추가하여 p95 Prometheus 기록.

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | **2.85s** | < 300ms | ❌ SLO 미달 |
| P90 응답 시간 | 2.59s | — | — |
| 평균 응답 시간 | 1.49s | — | — |
| 에러율 | **75.00%** | < 0.1% | ❌ (300건은 정상 409 DEPLETED) |
| orders_reserved | **100** | ≤ 100 | ✓ **오버셀 없음** |
| orders_cancelled | 300 | — | — |
| 총 요청 수 | 400 (200 VU × 2 iter) | — | — |
| 처리량 | 99.8 req/s | — | — |

> **에러율 75% 해석**: 200 VU 중 100건은 201 RESERVED, 300건은 409 DEPLETED(재고 소진). k6는 2xx 외 응답을 실패로 집계하므로 수치가 높게 나오나, 재고 100개 기준 정상 동작.  
> **성공 요청 P95**: `{ expected_response:true }` 기준 P95=975ms — 실제 처리 완료 요청의 응답시간.

### 오너 피드백 (형성빈)

**오버셀 발생 (핵심 버그)**: `orders_reserved=150` — 재고 100개 기준 50건 오버셀.  
`inventory.reserved_qty=100`(정상)과 `orders.status=RESERVED 150건` 불일치로 주문 생성(INSERT) 후 재고 차감(UPDATE) 사이 경쟁 조건으로 추정.

**확인 요청 사항:**
1. `POST /api/v1/orders` → inventory 차감 트랜잭션 경계 확인 (주문 생성과 재고 차감이 단일 TX인지)
2. Redis 분산 락 범위가 inventory 차감을 포함하는지 확인
3. `fandrops.queue.max-concurrent-processing` 코드 기본값 확인 및 적정값 결정 (200 VU 단일 배치 처리에 충분한지)

> **담당 분리**: 적정값 결정 → 형성빈, EC2 환경변수 주입 → 지영재  
> 현재 운영 환경에 해당 설정이 없어 코드 기본값으로 동작 중. 값이 200 미만이면 200 VU가 단일 배치로 처리되지 않아 동시성 재현이 부정확해짐.

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

### 결과 (2026-06-12, 4회차 — 공식 기록)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 | **231.01ms** | < 120ms | ❌ SLO 미달 |
| P90 응답 시간 | 206.18ms | — | — |
| 평균 응답 시간 | 136.41ms | — | — |
| 최소 응답 시간 | 5.59ms | — | — |
| 최대 응답 시간 | 1,060ms | — | — |
| 에러율 | 0.00% | < 0.1% | ✅ |
| 처리량 | 365.5 req/s | — | — |
| 총 요청 수 | 43,895 | — | — |

<details>
<summary>측정 이력</summary>

| 회차 | 날짜 | P95 | 비고 |
|---|---|---|---|
| 1회 | 2026-06-12 | 206ms | 모니터링 스택 추가 전 |
| 2회 | 2026-06-12 | 291ms | 모니터링 스택 동시 실행 |
| 3회 | 2026-06-12 | 287.67ms | Prometheus 스크레이프 수정 후 |
| 4회 | 2026-06-12 | 231.01ms | Grafana 캡처 병행, 공식 기록 채택 |

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

### 결과 (2026-06-17, 1회차 — **코드 버그로 재측정 필요**)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답 시간 (전체) | **40.32ms** | < 3,000ms | ✅ |
| P95 응답 시간 (성공 요청) | **119.32ms** | < 3,000ms | ✅ |
| 평균 응답 시간 | 20.43ms | — | — |
| 에러율 | **99.62%** | < 1% | ❌ (아래 해석 참고) |
| 성공 건수 | 1,257 / 337,891 | — | — |
| 처리량 | 2,111 req/s | — | — |
| 총 요청 수 | 337,891 | — | — |

> **에러율 99.62% 해석**: 정상적인 부하 측정 실패. 원인은 아래 두 가지 코드 버그.
>
> 1. **`TossConfirmBody` 직렬화 버그 ([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318))**: `TossPaymentGatewayAdapter` 내부 private record가 Jackson에 의해 직렬화되지 않아 Toss API 요청 body가 비어있는 상태로 전송됨 → Wiremock 404 → `payment.status = FAILED`
> 2. **409 DUPLICATE_PAYMENT 연쇄**: `payment.status = FAILED` 상태에서 같은 orderId 재호출 시 전부 409 반환 → ramping-vus 2m40s 동안 첫 50건 이후 전부 409
>
> Wiremock 임시 매핑 추가 후 일부(1,257건) 성공했으며, 성공 경로 P95 **119ms**는 의미 있는 참고 수치.

### 오너 피드백 (장성재)

**재측정 필요**: `TossConfirmBody` inner private record 직렬화 버그(이슈 [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)) 수정 후 재실행 필요.

**수정 방향**: `TossPaymentGatewayAdapter.TossConfirmBody`를 `Map.of()`로 교체하거나 `public`으로 변경.

```java
// 수정 예시 (Map.of 방식)
Map<String, Object> body = Map.of(
    "paymentKey", tossPaymentKey,
    "amount", amount,
    "orderId", orderPaymentKey
);
```

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
# DB: inventory 초기화 (reserved_qty=0 포함)
mysql -u fandrops_admin -pfandrops1234 \
  -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops \
  -e "UPDATE inventory SET available_qty=100, reserved_qty=0, version=0 WHERE product_id=1;"

# Redis: access ticket 재적재 (fan_id 1~2100) — valkey-cli --tls 필수
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

> ⚠️ **시나리오 05(SSE) 실행 후 시나리오 04를 실행하면 Redis 티켓이 UUID로 오염되어 전원 403 실패** — 반드시 04 먼저 실행. 상세: [이슈 #304](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/304)

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)" k6 run -e BASE_URL=http://localhost:$PORT --out experimental-prometheus-rw scenarios/04_drop_spike.js
```

> `K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"` — Prometheus에 p95/p99 게이지 메트릭 기록. 미설정 시 p99만 내보냄.

### 결과 (2026-06-17, 1회차 — **공식 베이스라인**)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답시간 (전체) | **12.34s** | < 300ms | ❌ SLO 미달 |
| P95 응답시간 (성공 요청) | **508ms** | < 300ms | ❌ SLO 미달 |
| P90 응답시간 | 11.33s | — | — |
| 평균 응답시간 | 4.63s | — | — |
| http_req_failed | **99.16%** | < 0.1% | ❌ (해석 아래 참고) |
| spike_orders_reserved | **100** | ≤ 100 | ✅ **오버셀 0건** |
| 총 요청 수 | 11,908 (157.6 req/s) | — | — |

**DB 사후 검증 (2026-06-17):**
```
inventory: available_qty=0, reserved_qty=100, total_qty=100
orders:    RESERVED=100, CANCELLED=8,433  (최근 30분 기준)
```
→ inventory reserved_qty=100 과 spike_orders_reserved=100 일치 — **오버셀 없음 확정**

> **에러율 99.16% 해석**: k6는 2xx 외 응답을 전부 실패로 집계. 실제 구성: 201 RESERVED 100건 + 409 DEPLETED 8,433건(재고 소진 정상 응답) + 기타(403/429/5xx) 3,375건. `checks_succeeded 71.65%`(8,533건)가 정상 처리 비율.  
> **Grafana 그래프 끊김**: 1,000 VU 스파이크 구간에서 t3.small CPU 포화로 Prometheus remote write 드롭 발생 — 공식 수치는 k6 터미널 기준 사용.  
> **성공 요청 P95 508ms**: `{ expected_response:true }` 기준 — 실제 처리 완료된 요청의 응답시간.

### 오너 피드백 (형성빈)

_결과 기록 완료 — 최적화 방향 업데이트 예정_

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
# Nginx worker_connections 확인 및 수정 (기본값 1024 → 4096)
grep worker_connections /etc/nginx/nginx.conf
sudo sed -i 's/worker_connections 1024/worker_connections 4096/' /etc/nginx/nginx.conf
sudo nginx -t && sudo nginx -s reload

# JVM FD 한계 확인
ulimit -n  # ≥ 8192 필요
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
ACTIVE=$(cat /etc/fandrops/active-slot)
PORT=$([ "$ACTIVE" = "blue" ] && echo 8081 || echo 8082)
K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)" k6 run -e BASE_URL=http://localhost:$PORT --out experimental-prometheus-rw scenarios/05_sse_queue.js
```

### 결과 (2026-06-17, 1회차 — **공식 베이스라인**)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| 정상 구간 에러율 (1,000 VU) | — | < 0.1% | ⚠️ 데이터 없음 |
| 경계 구간 에러율 (1,800 VU) | — | < 1% | ⚠️ 데이터 없음 |
| 초과 구간 429 발생 (2,100 VU) | — | count > 0 | ❌ 크래시로 미확인 |
| 429 retryable:true | — | 필수 | ❌ 크래시로 미확인 |

**실행 결과 요약:**

- `normal_load` (0→1,000 VU, 1m45s): **완료** ✓ — 1,000 complete iterations
- `boundary` (0→1,800 VU, 1m45s): **부분 실행** — 약 0m19s 진행 후 크래시
- `overflow` (0→2,100 VU, 1m15s): **미실행** — 크래시로 시작 못함

**크래시 원인 분석:**

t3.small(2GB RAM)에서 Spring Boot 2개 인스턴스 + k6 2,100 VU + Prometheus/Grafana 모니터링 스택을 동시 실행하여 OOM 발생. EC2 전체 다운, SSM 세션 단절, k6 터미널 결과 및 Prometheus 메트릭 전부 유실.

> **Grafana/Prometheus 데이터 없음**: EC2 OOM 크래시로 k6 remote write 전송 불가. 공식 수치로 사용할 터미널 출력도 유실됨.

### 오너 피드백 (장성재, 지영재)

**현상**: 2,100 VU SSE 동시 연결 시도 → t3.small OOM 크래시

**근본 원인 1 — k6와 앱 서버 동일 EC2 실행**

k6 2,100 VU 자체가 수백 MB 메모리를 점유. Spring Boot × 2 + k6 + 모니터링 스택의 메모리 합산이 2GB를 초과하여 OOM 발생.

**근본 원인 2 — Spring MVC blocking SSE**

Spring MVC SSE는 연결 1개당 Tomcat 스레드 1개를 점유. 2,100 연결 = 2,100 스레드 = 메모리 폭발 구조.

**개선 방향 (우선순위순):**

| 방법 | 효과 | 난이도 | 비용 |
|---|---|---|---|
| k6를 별도 머신에서 실행 (GitHub Actions runner 활용) | 테스트 환경 분리 → OOM 제거 | 낮음 | 0 |
| t3.medium 업그레이드 | RAM 2GB → 4GB | 낮음 | 비용 발생 |
| SSE 엔드포인트 Spring WebFlux 전환 | 연결당 메모리 ~1MB → ~50KB (20배↓), t3.small에서 2,100 VU 수용 가능 | 높음 | 0 |

> **재실행 전 필수 조치**: k6를 앱 서버와 분리하지 않으면 동일 크래시 재발. GitHub Actions `.github/workflows/run-k6.yml`을 활용하여 runner에서 실행하는 방식 권장.

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

### 결과 (2026-06-17, 1회차 — **t3.small 과부하 + 코드 버그로 재측정 필요**)

| 지표 | 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답시간 (전체) | **143.82ms** | < 300ms | ✅ (해석 주의 — 빠른 오류 응답 포함) |
| P95 응답시간 (성공 요청) | **2.48s** | < 300ms | ❌ SLO 미달 |
| P90 응답시간 | 119.6ms | — | — |
| 평균 응답시간 | 89.77ms | — | — |
| 에러율 | **99.43%** | < 0.1% | ❌ |
| checks_succeeded | 0.56% (773 / 137,264) | — | — |
| [feed] status 200 | **0%** (0 / 96,859) | — | ❌ |
| [queue] join 성공 | **2%** (773 / 31,523) | — | ❌ |
| [payment] confirm 성공 | **0%** (0 / 8,109) | — | ❌ |
| 처리량 | 382 req/s | — | — |
| 총 요청 수 | 137,305 | — | — |

**워크로드 분포 실측:**

| 워크로드 | 요청 수 | 설계 비율 | 실측 비율 |
|---|---|---|---|
| wl_feed | 96,912 | 60% | 70.6% |
| wl_queue | 32,315 | 20% | 23.5% |
| wl_order | 24,130 | 15% | 17.6% |
| wl_payment | 8,115 | 5% | 5.9% |

> **P95 143.82ms 해석 주의**: k6 threshold `p(95)<300` 은 통과했으나, 이는 빠른 오류 응답(timeout 이전 즉시 반환된 4xx/5xx)이 대다수이기 때문. 실제 성공한 요청의 P95는 **2.48s**로 SLO 초과.
>
> **에러율 99.43% 원인 두 가지:**
> 1. **결제 0%** — TossConfirmBody 직렬화 버그([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)), 시나리오 03과 동일
> 2. **피드 0% / 대기열 2%** — 혼합 150 VU에서 t3.small CPU 포화 → 전면 타임아웃. 시나리오 02(피드 50 VU 단독)는 231ms 통과했으나 혼합 부하에서 무너짐

### 오너 피드백 (전체)

**재측정 조건:**
1. k6를 EC2 외부(GitHub Actions runner)에서 실행하여 CPU 경합 제거
2. TossConfirmBody 버그([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)) 수정 후 실행

**도메인별 개선 방향:**

| 담당 | 문제 | 개선 방향 |
|---|---|---|
| 정환철 | 피드 0% — 혼합 부하에서 전면 실패 | N+1 쿼리 제거, `artist_feed.artist_id` 인덱스 확인, Redis TTL 캐싱 (시나리오 02 피드백 참고) |
| 장성재 | 결제 0% — TossConfirmBody 직렬화 버그 | [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318) `Map.of()` 교체 후 재측정 |
| 형성빈 | 주문 처리량 낮음 (17.6% 비율인데 오버셀 측정 불가) | 트랜잭션 경계·분산 락 범위 확인 (시나리오 01 피드백 참고) |
| 지영재 | k6 동일 EC2 실행으로 인한 인프라 병목 | `.github/workflows/run-k6.yml` Actions runner 실행 방식으로 전환 |

---

## 전체 결과 요약

| 시나리오 | 담당 | P95 | 에러율 | 오버셀 | 상태 |
|---|---|---|---|---|---|
| 01 주문 동시성 | 형성빈 | 2.85s | 75%\* | 0건 | ❌ SLO 미달 |
| 02 피드 조회 | 정환철 | 287.67ms | 0.00% | — | ❌ SLO 미달 |
| 03 결제 확인 | 장성재 | 119.32ms (성공 기준) | 99.62%\*\*\*\* | — | ❌ 코드 버그([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)) 재측정 필요 |
| 04 드롭스 스파이크 | 형성빈 | 12.34s (성공 508ms) | 99.16%\*\* | 0건 | ❌ SLO 미달 |
| 05 SSE 대기열 | 장성재, 지영재 | — | —\*\*\* | — | ❌ OOM 크래시 |
| 06 통합 워크로드 | 전체 | 2.48s (성공 기준) | 99.43%\*\*\*\*\* | — | ❌ t3.small 과부하 + 코드 버그, 재측정 필요 |

> \* 시나리오 01 에러율 75%: 200 VU 중 300건이 409 DEPLETED(재고 소진 정상 응답), 100건 201 RESERVED. 오버셀 없음.  
> \*\* 시나리오 04 에러율 99.16%: 1,000 VU 중 100건 201 RESERVED + 8,433건 409 DEPLETED(정상) + 3,375건 기타. k6는 2xx 외 응답을 전부 실패로 집계. 오버셀 없음.  
> \*\*\* 시나리오 05: 2,100 VU SSE 동시 연결로 t3.small OOM 크래시 — k6 터미널 출력 및 Prometheus 메트릭 전부 유실. normal_load(1,000 VU)만 완료 확인. 재실행 전 k6를 별도 머신에서 실행하거나 Spring WebFlux 전환 필요.  
> \*\*\*\* 시나리오 03 에러율 99.62%: `TossConfirmBody` inner private record Jackson 직렬화 불가 → Toss API 요청 body 비어있음 → Wiremock 404 → payment FAILED → 이후 전부 409 DUPLICATE_PAYMENT 연쇄. 이슈 [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318) 수정 후 재측정 필요.  
> \*\*\*\*\* 시나리오 06 에러율 99.43%: 혼합 150 VU에서 t3.small CPU 포화로 피드/대기열 전면 타임아웃 + TossConfirmBody 버그(#318)로 결제 0% 성공. P95 143.82ms는 빠른 오류 응답이 대부분이므로 misleading — 성공 요청 P95 2.48s가 실질 지표.

---

## 최적화 우선순위

| 우선순위 | 담당 | 시나리오 | 개선 방향 | 기대 효과 |
|---|---|---|---|---|
| P0 | 장성재 | 03·06 결제 | `TossConfirmBody` → `Map.of()` 교체 ([#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318)) | 결제 0% → 정상 측정 가능 |
| P0 | 정환철 | 02·06 피드 | N+1 쿼리 제거, `artist_feed.artist_id` 인덱스, Redis TTL 캐싱 | P95 231ms → 120ms 목표 |
| P0 | 지영재 | 전체 | k6를 GitHub Actions runner에서 실행 (EC2 분리) | CPU 경합 제거, 05·06 정확한 재측정 가능 |
| P1 | 형성빈 | 01·04·06 주문 | 트랜잭션 경계 확인, 분산 락 범위 재검토 | P95 2.85s → 300ms 목표 |
| P2 | 장성재, 지영재 | 05·06 SSE | Spring WebFlux 전환 검토 (연결당 스레드 → 코루틴) | 2,100 VU t3.small 수용 가능 |

---

## 트러블슈팅 이력 (2026-06-17)

### 1. EC2 재기동 시 RDS도 중지 상태

**현상**: EC2 시작 후 Spring Boot 앱이 `HikariPool` 커넥션 획득 실패로 크래시.  
**원인**: EC2와 RDS를 각각 수동으로 중지했다가 EC2만 재시작. RDS는 별도로 중지 상태 유지.  
**해결**: AWS 콘솔에서 RDS 인스턴스 별도 시작 → 앱 재기동.  
**교훈**: EC2 재기동 시 RDS·ElastiCache 상태를 함께 확인해야 함.

---

### 2. TOKEN 변수 newline 포함 → HTTP 헤더 파싱 실패

**현상**: `curl` 요청 시 400 HTML 응답(Tomcat 기본 에러 페이지).  
**원인**: `TOKEN=$(aws secretsmanager ...)` 출력에 `\r\n` 포함 → `Authorization: Bearer <token>\r\n` 헤더가 두 줄로 분리되어 파싱 실패.  
**해결**:
```bash
TOKEN=$(aws secretsmanager get-secret-value ... | jq -r '.token' | tr -d '\r\n')
```
**교훈**: 환경변수로 토큰을 다룰 때 항상 `tr -d '\r\n'` 적용.

---

### 3. orders.json 생성 시 awk → Python3 교체

**현상**: awk로 생성한 JSON을 `python3 -m json.tool`로 검증하면 `Expecting value: line 2 column 1` 오류.  
**원인**: awk의 printf에서 탭·개행 이스케이프 처리 불안정.  
**해결**: Python3 원라이너로 교체.
```bash
mysql ... | python3 -c "
import sys, json
rows = [l.split('\t') for l in sys.stdin.read().strip().split('\n')]
print(json.dumps([{'orderId':int(r[0]),'fanId':int(r[1]),'amount':float(r[2]),'orderPaymentKey':r[3]} for r in rows]))
"
```

---

### 4. Wiremock 위치 혼동 (JAR vs Docker)

**현상**: `/opt/fandrops/wiremock/` 디렉터리 없음, JAR 파일도 없음.  
**원인**: Wiremock이 Docker 컨테이너로 실행 중이었음. `docker ps`로 확인 가능.  
**해결**: 추가 설치 불필요. Docker 컨테이너가 이미 포트 8090을 점유 중.
```bash
docker ps  # wiremock 컨테이너 확인
curl -s http://localhost:8090/__admin/mappings | python3 -m json.tool
```
**교훈**: Wiremock 실행 확인은 `docker ps` 먼저.

---

### 5. DB reset 후 OrderRecoveryScheduler가 주문 즉시 취소

**현상**: DB를 RESERVED로 리셋한 직후 k6 실행하면 전부 409 DUPLICATE\_PAYMENT 또는 404.  
**원인**: `UPDATE orders SET status='RESERVED' WHERE ...` 시 `updated_at` 미업데이트 → 기존 `updated_at`(수십 분 전)이 유지됨 → `OrderRecoveryScheduler`(60초 주기, 30분 타임아웃)가 즉시 CANCEL 처리.  
**해결**: reset SQL에 `updated_at=NOW()` 추가.
```sql
UPDATE orders SET status='RESERVED', updated_at=NOW() WHERE id BETWEEN 51 AND 100;
```

---

### 6. TossConfirmBody inner private record Jackson 직렬화 불가

**현상**: 시나리오 03 전체 99.62% 실패. Wiremock 요청 로그에서 body가 빈 문자열.  
**원인**: `TossPaymentGatewayAdapter` 내부에 `record TossConfirmBody(...)` 를 package-private으로 선언. Jackson 기본 설정은 public 클래스만 직렬화 가능 → body `{}` 또는 빈 문자열 전송 → Wiremock `bodyPatterns` 불일치 → 404 → `payment.status=FAILED` → 이후 동일 orderId 전부 409 DUPLICATE\_PAYMENT 연쇄.  
**임시 해결**: Wiremock에 `bodyPatterns` 없는 priority:1 매핑 추가.
```bash
curl -s -X POST http://localhost:8090/__admin/mappings \
  -H 'Content-Type: application/json' \
  -d '{"priority":1,"request":{"method":"POST","url":"/v1/payments/confirm"},"response":{"status":200,"headers":{"Content-Type":"application/json"},"body":"{\"paymentKey\":\"success-mock\",\"orderId\":\"mock\",\"totalAmount\":15000,\"status\":\"DONE\"}"}}'
```
**근본 해결**: 이슈 [#318](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/issues/318) — `TossConfirmBody`를 `Map.of()`로 교체 (담당: 장성재).

---

### 7. inventory total_qty invariant 위반

**현상**: `UPDATE inventory SET available_qty=200, reserved_qty=0 WHERE product_id=1` 실행 후 `total_qty=50` 그대로 → `available_qty(200) > total_qty(50)`.  
**원인**: reset SQL에 `total_qty` 미포함.  
**해결**: reset 시 `total_qty`도 함께 업데이트.
```sql
UPDATE inventory SET available_qty=200, reserved_qty=0, total_qty=200, version=0 WHERE product_id=1;
```
**교훈**: inventory reset SQL은 항상 `available_qty + reserved_qty = total_qty` 불변식을 유지해야 함.