# k6 부하 테스트 — 시나리오별 설명 및 베이스라인 결과

> **목적**: 코드 버그 탐색·실행 환경 검증·오버셀 정합성 확인 (예비 측정)
> **실행 환경**: EC2 t3.small (단일 인스턴스) — Spring Boot + Prometheus + Grafana + k6 동시 실행
> **실행일**: 2026-06-12 ~ 2026-06-17
> **기준 SLO**: `docs/observability-metrics.md` 참고

> ⚠️ **이 문서의 수치는 공식 SLO 베이스라인이 아닙니다.**
>
> k6를 앱 서버와 같은 EC2에서 실행하면 CPU·메모리 경합으로 레이턴시가 실제보다 높게 측정됩니다.
> 이 측정의 가치는 **코드 버그 발견(s03 #318), 실행 순서 의존성 확인, 오버셀 0건 검증**에 있습니다.
>
> **공식 SLO 베이스라인 및 최적화 전/후 비교는 EC2-2 전용 k6 runner에서 측정합니다.**
> EC2-2 기동 후 최적화 전 수치를 먼저 측정하고, 최적화 완료 후 동일 환경에서 재측정해야 개선폭이 유효합니다.
> 자세한 절차: `aws-phase4-runbook.md § 4-7`

---

## 테스트 환경 공통 사항

### 예비 측정 환경 (이 문서)

| 항목 | 값 |
|---|---|
| EC2 인스턴스 | t3.small (2 vCPU, 2 GB RAM) |
| k6 실행 위치 | 동일 EC2 (CPU·메모리 경합 발생) |
| Spring Boot | Blue/Green 슬롯 중 활성 슬롯 직접 접근 (`:8081` 또는 `:8082`) |
| DB | RDS MySQL (별도 인스턴스) |
| Redis | ElastiCache (별도 인스턴스) |
| 모니터링 | Prometheus + Grafana (동일 EC2) |
| 토큰 | `infra/k6/seed/tokens.csv` — fan_id 1~2100 JWT |

### 공식 SLO 측정 환경 (EC2-2 기동 후)

| 항목 | 값 |
|---|---|
| k6 실행 위치 | EC2-2 t3.small 전용 runner (서울 리전, 경합 없음) |
| 측정 대상 | EC2-1 Spring Boot (active 슬롯) |
| 네트워크 | 동일 리전 내부 → 네트워크 오버헤드 없음 |
| 적용 시나리오 | s01·s02·s03·s04·s06 (s05는 Actions runner 유지) |

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

드롭스 오픈런 상황에서 팬들이 동시에 주문을 요청할 때 재고 오버셀이 발생하지 않는지 검증하는 핵심 시나리오다.

FANDROPS의 주문 흐름은 **대기열 진입 → accessToken 발급 → 주문 API 호출** 순서로 진행된다. 재고 차감은 Redis 분산 락(`reserveAtomic`) + DB `WHERE available_qty >= qty` 원자적 UPDATE로 보호되며, 200 VU가 동시에 발화해도 `orders_reserved ≤ 100`(재고 수량)이어야 한다.

검증 핵심:
- **오버셀 0건**: `SELECT COUNT(*) FROM orders WHERE status = 'RESERVED'` = 100
- **락 정합성**: Redis 분산 락이 동시 요청을 직렬화하는지
- **대기열 처리량**: 200 VU가 단일 스케줄러 배치로 처리되는지 (`max-concurrent-processing` 설정값 영향)

실패 시 나타나는 현상: `orders_reserved > 100` → 재고보다 많은 주문이 RESERVED 상태 → 결제 단계에서 데이터 불일치.

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

### 사후 처리 (04 실행 전 필수)

주문 성공 시 `access:ticket:1:{fanId}` 키가 삭제되므로, **04 실행 전에 반드시 Redis 재적재** 필요.

```bash
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

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

팬이 아티스트 피드를 조회하는 가장 빈번한 읽기 동작의 성능 기준선을 측정한다. 드롭스 오픈런 전후로 팬들이 아티스트 피드를 집중적으로 조회하는 패턴을 재현한다.

Read SLO(P95 < 120ms)는 쓰기 SLO(300ms)보다 엄격하다. 이 엔드포인트는 인증 없이 `permitAll`로 누구나 호출 가능하므로 트래픽 집중 시 가장 먼저 병목이 나타난다.

검증 핵심:
- **P95 < 120ms**: 50 VU 2분 지속 부하에서 달성 여부
- **N+1 쿼리 여부**: 피드 목록 조회 시 아티스트·이미지·좋아요 카운트 등을 별도 쿼리로 N번 조회하는지
- **캐시 효과**: Redis 캐싱 적용 전후 응답 시간 차이

실패 시 나타나는 현상: P95 > 120ms → 오픈런 시 피드 페이지 로딩 지연 → 팬 이탈 증가.

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

### 사후 처리

DB·Redis 상태를 변경하지 않으므로 별도 정리 불필요. 다음 시나리오 바로 실행 가능.

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

주문 후 결제 확인 단계에서 Toss PG 응답 지연·오류 상황에서도 중복 결제 없이 정상 처리되는지 검증한다. 실제 Toss API를 호출하면 비용이 발생하고 외부 의존성이 생기므로 Wiremock으로 PG를 모킹해 제어된 환경에서 부하를 가한다.

결제 확인은 idempotency_key + payment_key unique constraint로 중복 결제를 방지한다. 타임아웃·서버 오류 상황에서 재시도가 들어올 때 이 제약이 올바르게 동작하는지가 핵심이다.

검증 핵심:
- **중복 결제 0건**: 동일 `payment_key`로 중복 confirm 요청 시 409 반환 여부
- **타임아웃 처리**: Wiremock 지연 응답(2,000ms) 시 앱이 적절히 처리하는지
- **P95 < 3,000ms**: 결제 SLO — PG 응답 대기 포함
- **에러율 < 1%**: 성공 시나리오 기준

실패 시 나타나는 현상: idempotency 미동작 → 동일 주문에 중복 결제 → 환불 처리 비용 및 정합성 오류.

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

### 사후 처리 (재실행 시 필수)

결제 실패 레코드와 취소된 주문이 남아 있으면 재실행 시 전부 409. 반드시 초기화 후 재실행.

```bash
MYSQL="mysql -u fandrops_admin -p<PW> -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"

# payment 삭제 + orders RESERVED 복원 (updated_at=NOW() 필수 — 누락 시 OrderRecoveryScheduler가 즉시 취소)
$MYSQL -e "DELETE FROM payment WHERE order_id BETWEEN 51 AND 100;"
$MYSQL -e "UPDATE orders SET status='RESERVED', updated_at=NOW() WHERE id BETWEEN 51 AND 100;"

# 확인
$MYSQL -e "SELECT COUNT(*) FROM payment WHERE order_id BETWEEN 51 AND 100;"         # → 0
$MYSQL -e "SELECT COUNT(*) FROM orders WHERE status='RESERVED' AND id BETWEEN 51 AND 100;" # → 50
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

드롭스 상품이 오픈되는 순간 팬들이 일제히 몰려드는 오픈런 트래픽을 재현한다. 0 → 1,000 VU가 30초 만에 급증하는 `ramping-vus` 패턴으로 Rate Limit, 대기열, 재고 차감이 스파이크 하에서도 정합성을 유지하는지 확인한다.

s01(200 VU 동시성)과 달리 스파이크 자체가 검증 대상이다. 램프업 구간에서 들어온 요청이 Rate Limit(`5r/s`)에 의해 큐에 쌓이고, 대기열 스케줄러가 처리하는 동안 오버셀이 발생하지 않아야 한다.

검증 핵심:
- **오버셀 0건**: 1,000 VU 스파이크 중 `orders_reserved ≤ 100`
- **Rate Limit 동작**: Nginx `limit_req zone=fandrops_order` 초과 요청이 429로 처리되는지
- **스파이크 중 P95**: 정상 처리된 요청(`expected_response:true`) 기준 응답 시간
- **에러율 해석**: 409 DEPLETED(재고 소진)·429(Rate Limit)는 정상 동작, 500은 이상

실패 시 나타나는 현상: 스파이크 중 분산 락 타임아웃 → 락 획득 실패 → 오버셀 또는 대량 500.

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

### 사후 처리 (06 실행 전 필수)

04 완료 후 inventory와 Redis가 소진 상태. 06 실행 전 반드시 초기화.

```bash
MYSQL="mysql -u fandrops_admin -p<PW> -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"

# 06용 inventory 리셋 (200개)
$MYSQL -e "UPDATE inventory SET available_qty=200, reserved_qty=0, total_qty=200, version=0 WHERE product_id=1;"

# Redis 티켓 재적재
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

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

### 오너 피드백 (형성빈 · 측정: 지영재)

**핵심 성과**: 오버셀 0건 — 시나리오 01 1회차(오버셀 50건) 수정 이후 1,000 VU 스파이크에서도 재고 정합성 유지 확인.

**성능**: P95 성공 요청 508ms — SLO(300ms) 미달. 단, k6를 EC2와 동일 인스턴스에서 실행한 환경 특성상 tail latency가 부풀려짐. **Actions runner 전환 후 재측정 필요.**

**확인 요청 사항:**

1. **트랜잭션 경계 재확인**: 1,000 VU 스파이크 환경에서 주문 생성 + 재고 차감이 단일 TX로 묶여 있는지 확인 (시나리오 01 피드백과 동일)
2. **분산 락 경합**: 0→1,000 VU 30초 급상승 구간에서 Redis 분산 락 획득 대기가 P95 508ms의 주 원인인지 락 획득 대기 시간 로그로 확인 권장
3. **재측정**: k6 Actions runner 전환 후 동일 시나리오 재실행하여 CPU 경합 없는 환경에서 P95 수치 재확인

---

## 시나리오 05: SSE 대기열 연결 안정성 (SSE Queue)

**파일**: `infra/k6/scenarios/05_sse_queue.js`
**담당 오너**: 장성재, 지영재
**SLO**: 정상 구간 에러율 < 0.1%, 경계 구간 에러율 < 1%, 2,100 VU 초과 시 429 응답 필수

### 목적

드롭스 오픈런 시 팬들이 대기열 상태를 실시간으로 전달받는 SSE 연결의 안정성을 검증한다. SSE는 HTTP 연결을 장시간 유지하는 방식이라 일반 API와 달리 동시 연결 수가 서버 FD(File Descriptor)·Nginx worker_connections 한계에 직접 영향을 받는다.

3단계 VU 증가로 각 구간의 동작을 구분해 확인한다.

| 구간 | VU | 검증 목표 |
|---|---|---|
| 정상 | 1,000 VU | 에러율 < 0.1% — 안정적 연결 유지 |
| 경계 | 1,800 VU | 에러율 < 1% — 한계 근접 동작 확인 |
| 초과 | 2,100 VU | 429 `retryable:true` 응답 계약 이행 여부 |

검증 핵심:
- **연결 안정성**: 1,000 VU 구간에서 SSE 스트림이 끊기지 않고 유지되는지
- **429 계약**: 2,100 VU 초과 시 서버가 적절히 거부 응답(`retryable:true`)을 반환하는지 — 클라이언트가 재시도 가능한 형태로 처리됨
- **Nginx FD 한계**: `worker_connections ≥ 2048` 설정 하에서 연결 거부가 발생하지 않는지
- **JVM FD**: `ulimit -n ≥ 8192` 환경에서 소켓 고갈 없는지

실패 시 나타나는 현상: 연결 수 한계 초과 시 무응답(connection timeout) → 팬이 대기열 상태를 받지 못해 오픈런 참여 불가.

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

개별 시나리오(s01~s05)는 단일 엔드포인트만 검증하지만, 실제 서비스에서는 피드 조회·주문·결제가 동시에 발생한다. s06은 실제 트래픽 비율을 반영한 혼합 부하로 전체 SLO를 한 번에 측정한다.

개별 테스트에서는 발견되지 않는 **시스템 전체 병목**이 드러난다. 예를 들어 피드 조회(Read)가 DB 커넥션 풀을 점유하면 동시 주문(Write) 응답 시간이 올라가는 식의 간섭 효과를 확인할 수 있다.

트래픽 구성:
- 피드 조회(Read): 다수 VU — 가장 빈번한 동작
- 주문(Write): 중간 VU — 오픈런 구간 집중
- 결제 확인(Write): 소수 VU — 주문 완료 후 후속 동작

검증 핵심:
- **혼합 P95 < 300ms**: 모든 Write 엔드포인트가 동시 부하 하에서도 SLO 유지
- **Read P95 < 120ms**: 피드 조회가 주문·결제 트래픽에 영향받지 않는지
- **에러율 < 0.1%**: 혼합 부하에서 커넥션 풀 고갈·타임아웃 없는지
- **오버셀 0건**: 혼합 트래픽 중 주문 정합성 유지

실패 시 나타나는 현상: 특정 엔드포인트가 DB 커넥션 풀을 독점 → 다른 요청 전체 타임아웃 → 서비스 전체 다운.

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

### 사후 처리 (전체 테스트 완료 후)

06이 마지막 시나리오. 완료 후 S3 seed 파일과 Redis 테스트 키를 삭제.

```bash
# S3 seed 파일 삭제 (유효한 JWT·주문 정보 — 테스트 완료 후 즉시 삭제)
aws s3 rm s3://<BUCKET>/k6/tokens.csv
aws s3 rm s3://<BUCKET>/k6/orders.json

# Redis AccessTicket 테스트용 키 삭제
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"
for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls del "access:ticket:1:$i"
done
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

---

## 트러블슈팅 이력 (2026-06-18) — Actions runner 첫 실행

### 8. IAM S3 GetObject 권한 누락 → seed 파일 다운로드 403

**현상**: Actions runner에서 `aws s3 cp s3://<BUCKET>/k6/tokens.csv` 실행 시 `An error occurred (403) Forbidden`.  
**원인**: `fandrops-github-actions-role`에 `k6/*` prefix에 대한 `s3:GetObject` 권한 없음. 기존 정책은 `deploy/*`만 허용.  
**해결**: IAM 인라인 정책 `fandrops-k6-seed-policy` 추가.
```json
{
  "Effect": "Allow",
  "Action": "s3:GetObject",
  "Resource": "arn:aws:s3:::<BUCKET>/k6/*"
}
```

---

### 9. Nginx 502 Bad Gateway — `proxy_pass` 포트 하드코딩

**현상**: k6 모든 요청 502 반환. `curl http://localhost/api/v1/...` → 502.  
**원인**: `/etc/nginx/default.d/fandrops-location.conf`에 `proxy_pass http://localhost:8080` 하드코딩. CD 배포 시 active 슬롯이 green(:8082)으로 전환되었으나 location.conf는 8080 그대로 유지.  
**진단**: `cat /etc/nginx/fandrops-active.conf` → `server 127.0.0.1:8082` (정상), `cat /etc/nginx/default.d/fandrops-location.conf` → `localhost:8080` (하드코딩 확인).  
**임시 해결**: `sudo sed -i 's/localhost:8080/localhost:8082/g' /etc/nginx/default.d/fandrops-location.conf && sudo nginx -s reload`  
**근본 해결**: CD 파이프라인에 nginx 설정 파일 동기화 단계 추가 (트러블슈팅 12 참고).

---

### 10. Nginx `"upstream" directive is not allowed here`

**현상**: `sudo nginx -t` → `"upstream" directive is not allowed here in /etc/nginx/default.d/fandrops-location.conf:1`.  
**원인**: `fandrops-location.conf` 첫 줄의 `include /etc/nginx/fandrops-active.conf`가 `default.d/`(server context)에서 로드됨. `fandrops-active.conf`의 `upstream` 블록은 `http` context에서만 허용.  
**해결**:
1. `/etc/nginx/conf.d/fandrops-upstream.conf` 신규 생성 (http context) — `include` + zone 정의 포함
2. `/etc/nginx/default.d/fandrops-location.conf`에서 `include` 줄 제거
3. repo `nginx/fandrops-location.conf`도 동일하게 수정

```nginx
# /etc/nginx/conf.d/fandrops-upstream.conf
include /etc/nginx/fandrops-active.conf;
limit_req_zone  $binary_remote_addr zone=fandrops_order:10m   rate=5r/s;
...
```

---

### 11. `zero size shared memory zone "fandrops_order"`

**현상**: nginx -t → `nginx: [emerg] zero size shared memory zone "fandrops_order"`.  
**원인**: `nginx/fandrops-zones.conf`(repo)가 한 번도 EC2에 배포된 적 없음. `limit_req zone=fandrops_order` 지시어는 있으나 `limit_req_zone` 정의가 없어 크기가 0.  
**해결**: 트러블슈팅 10에서 생성한 `conf.d/fandrops-upstream.conf`에 zone 정의 포함. nginx -t 통과.

---

### 12. CD 파이프라인 nginx 설정 파일 drift

**현상**: CD 배포 후 nginx 설정이 repo와 달라 수동 수정 필요. 매 배포 시 재발 가능.  
**원인**: `bluegreen-deploy.sh`가 `fandrops-active.conf`(포트)만 갱신. `fandrops-location.conf`·`fandrops-upstream.conf`는 배포 대상에 없어 EC2 설정이 repo와 diverge.  
**해결**:
- `nginx/fandrops-upstream.conf` 신규 추가
- `nginx/fandrops-location.conf`에서 `include` 줄 제거
- `cd.yml`: nginx 설정 파일 S3 업로드 단계 추가
- `bluegreen-deploy.sh`: step 5에 S3 → EC2 nginx 동기화 추가

이후 배포마다 nginx 설정이 repo 기준으로 자동 동기화됨.

---

### 13. JWT 403 — tokens.csv 서명 secret 불일치

**현상**: k6 100% 403 응답. 응답 body 없음(Spring Security filter 단에서 차단).  
**원인**: S3의 `tokens.csv`가 이전 테스트용 secret으로 서명됨. 앱 서버는 실제 운영 `JWT_SECRET`으로 검증 → 서명 불일치 → 403.  
**진단 과정**:
1. `curl .../actuator/health` → 200 (앱 정상)
2. 인증 없는 공개 엔드포인트 → 200 (Nginx 정상)
3. Bearer 토큰 포함 요청 → 403, body 없음 → JWT 검증 실패 확인
4. EC2 `/etc/fandrops/fandrops-prod.conf`에서 `JWT_SECRET` 확인 → tokens.csv 서명 secret과 불일치 확인
5. 올바른 secret으로 fan_id 1~2100 JWT 재생성 → S3 재업로드 → 정상

> ⚠️ `JWT_SECRET`은 `/etc/fandrops/fandrops-prod.conf`에만 보관. 코드·커밋에 절대 기록 금지.

**tokens.csv 만료 일정**: 7일 유효기간 → **2026-06-25까지** 재생성 필요.

---

### 14. k6 exit code 99 → Actions job 실패 표시

**현상**: k6 p(95)=216ms > threshold 120ms → exit 99 → Actions job 빨간불. 인프라 정상인데 전체 실패처럼 보임.  
**원인**: k6 threshold 미달 시 exit 99 반환. GitHub Actions는 exit 0 이외를 job 실패로 처리.  
**해결**: `run-k6.yml` k6 실행 단계에 exit code 분기 추가.
- exit 99: job 성공 + `::warning::` 배너 (SLO 미달 경고)
- 그 외 non-zero: job 실패 (실제 오류)
