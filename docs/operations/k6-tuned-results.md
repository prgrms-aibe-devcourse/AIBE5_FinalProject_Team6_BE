# k6 튜닝 후 SLO 재측정 결과 (EC2-2 전용 측정)

> **목적**: 베이스라인 피드백 반영 후 SLO 달성 여부 재검증
> **실행 환경**: EC2-2 t3.small (k6 전용 러너) → EC2-1 Spring Boot (api.fandrops.site)
> **실행일**: 2026-06- ~
> **기준 SLO**: `docs/observability-metrics.md` 참고
> **이전 결과**: `docs/operations/k6-baseline-results.md`

---

## 테스트 환경

| 항목 | 값 |
|---|---|
| k6 실행 위치 | EC2-2 t3.small (서울 리전, Spring Boot 없음) |
| 측정 대상 | EC2-1 Spring Boot — `https://api.fandrops.site` (VPC 내부 사설 IP) |
| 네트워크 | 동일 VPC 내부 통신 — 네트워크 오버헤드 없음 |
| DB | RDS MySQL (별도 인스턴스) |
| Redis | ElastiCache (별도 인스턴스) |
| 모니터링 | Prometheus Remote Write → EC2-1 (`http://10.0.1.114:9090/api/v1/write`) |
| 토큰 | `/opt/fandrops/k6/seed/tokens.csv` — fan_id 1~2100 JWT |
| 적용 시나리오 | s01·s02·s03·s04·s06 (s05는 Actions runner 유지) |

---

## 공통 실행 명령어 패턴

```bash
cd /opt/fandrops/k6
export BASE_URL=https://api.fandrops.site
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

k6 run -e BASE_URL=$BASE_URL \
  --out experimental-prometheus-rw \
  scenarios/<번호>_<이름>.js
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

## 권장 실행 순서

| 순서 | 시나리오 | 사전 준비 | 실행 위치 |
|---|---|---|---|
| 1 | s02 피드 Read | 없음 | EC2-2 |
| 2 | s01 주문 동시성 | inventory 리셋(100) + Redis 티켓 재적재 | EC2-2 |
| 3 | s04 드롭스 스파이크 | inventory 리셋(100) + Redis 티켓 재적재 | EC2-2 |
| 4 | s03 결제 확인 | Wiremock 기동 확인 | EC2-2 |
| 5 | s05 SSE 대기열 | — | **Actions runner** |
| 6 | s06 통합 워크로드 | inventory 리셋(200) + Wiremock 확인 | EC2-2 |

> ⚠️ **s05 먼저 실행 금지**: s05 실행 후 Redis 티켓이 UUID로 오염되어 s01·s04 전원 403 실패. 반드시 s04 이후 s05 실행.

---

## 시나리오 02: 피드 조회 Read P95

**파일**: `infra/k6/scenarios/02_feed_read.js`
**담당 오너**: 정환철
**SLO**: P95 < 120ms, 에러율 < 0.1%

### 이전 피드백 (정환철)

> 출처: `k6-baseline-results.md` — 오너 피드백 (→ 정환철)

- P95 133ms로 SLO(120ms) 13ms 미달입니다. 초반 캐시 워밍업 구간에서 250ms까지 튀는 게 집계 수치를 끌어올리고 있어서, 워밍업 트래픽 인가 또는 TTL jitter 범위 축소를 검토해주세요.
- Redis P95 레이턴시 초반 25ms 피크가 캐시 미스 시 DB 쿼리에서 오는 것으로 보입니다. 피드 조회 쿼리 실행 계획(EXPLAIN) 한 번 확인 부탁드립니다.

### 피드백 반영 내용 (정환철)

> (정환철 작성)

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

### 실행 명령어

```bash
cd /opt/fandrops/k6
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

k6 run -e BASE_URL=https://api.fandrops.site \
  --out experimental-prometheus-rw \
  scenarios/02_feed_read.js
```

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답 시간 | 133.02ms | — | < 120ms | 미측정 |
| P90 응답 시간 | 110.6ms | — | — | — |
| 평균 응답 시간 | 67.08ms | — | — | — |
| 에러율 | 0.00% | — | < 0.1% | 미측정 |
| 처리량 | 741 RPS | — | — | — |

### 스크린샷

> `screenshots/tuned/s02_feed_read_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 01: 주문 동시성 (Order Concurrency)

**파일**: `infra/k6/scenarios/01_order_concurrency.js`
**담당 오너**: 형성빈
**SLO**: `orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 이전 피드백 (형성빈)

> 출처: `k6-baseline-results.md` — 오너 피드백 (→ 형성빈)

- P95 865ms(성공 요청 기준)로 SLO(300ms) 약 3배 초과입니다. 오버셀은 0건으로 정합성은 완벽합니다.
- 200 VU 동시 발화 시 Redis 분산 락 직렬화 대기가 병목으로 추정됩니다. `reserveAtomic` Lua 스크립트 실행 시간 및 락 경합 현황 확인 부탁드립니다.
- DB `available_qty` 조건 UPDATE 실행 계획(EXPLAIN)도 함께 확인해주세요.

### 피드백 반영 내용 (형성빈)

> (형성빈 작성)

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

### 사전 준비

```bash
MYSQL="mysql -u fandrops_admin -pfandrops1234 -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"

$MYSQL -e "UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;"

for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

### 실행 명령어

> ⚠️ **BASE_URL 예외**: s01은 `http://10.0.1.114:8081` (Spring Boot 직접 연결, Nginx 우회).
> EC2-2 단일 IP에서 200 VU 발화 시 Nginx IP 기반 rate limit이 대부분 차단함. 실제 프로덕션에서는 200명이 각자 다른 IP로 요청하므로 해당 제한이 적용되지 않는다. s01 검증 목적(오버셀 방지)과 무관한 아티팩트이므로 Nginx를 우회한다.

```bash
cd /opt/fandrops/k6
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

k6 run -e BASE_URL=http://10.0.1.114:8081 \
  -e FAN_POOL_SIZE=200 \
  --out experimental-prometheus-rw \
  scenarios/01_order_concurrency.js
```

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 (전체) | 1,750ms | — | < 300ms | 미측정 |
| P95 응답시간 (성공 요청) | 865ms | — | < 300ms | 미측정 |
| 평균 응답시간 | — | — | — | — |
| 에러율 | 75%\* | — | < 0.1%\* | 미측정 |
| orders_reserved | 100건 (오버셀 0건 ✅) | — | ≤ 100 | 미측정 |
| 처리량 | — | — | — | — |

### 스크린샷

> `screenshots/tuned/s01_order_concurrency_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 04: 드롭스 스파이크 (Drop Spike)

**파일**: `infra/k6/scenarios/04_drop_spike.js`
**담당 오너**: 형성빈
**SLO**: `spike_orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 이전 피드백 (형성빈)

> 출처: `k6-baseline-results.md` — 오너 피드백 (→ 형성빈)

- P95 266ms로 SLO(300ms) 달성, 오버셀 0건 확인입니다. ✅
- Nginx rate limit이 스파이크를 흡수해서 앱 서버가 보호된 결과입니다. 성공 요청 P95 640ms는 s01과 동일하게 Redis 분산 락 경합이 원인으로 추정됩니다. 락 최적화 검토 부탁드립니다.

### 피드백 반영 내용 (형성빈)

> (형성빈 작성) — 베이스라인에서 SLO 달성. 추가 개선 사항이 있으면 작성.

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

### 사전 준비

```bash
MYSQL="mysql -u fandrops_admin -pfandrops1234 -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"

$MYSQL -e "UPDATE inventory SET available_qty=100, reserved_qty=0, total_qty=100, version=0 WHERE product_id=1;"

for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

k6 run -e BASE_URL=https://api.fandrops.site \
  --out experimental-prometheus-rw \
  scenarios/04_drop_spike.js
```

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 (전체) | 266.24ms | — | < 300ms | 미측정 |
| P95 응답시간 (성공 요청) | 640ms | — | 참고값 | 미측정 |
| 에러율 | 99.98%\* | — | < 0.1%\* | 미측정 |
| spike_orders_reserved | 100건 (오버셀 0건 ✅) | — | ≤ 100 | 미측정 |
| 처리량 | — | — | — | — |

### 스크린샷

> `screenshots/tuned/s04_drop_spike_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 03: 결제 확인 (Payment Confirm)

**파일**: `infra/k6/scenarios/03_payment_confirm.js`
**담당 오너**: 장성재
**SLO**: P95 < 3,000ms, 에러율 < 1%

### 이전 피드백 (장성재)

> 출처: `k6-baseline-results.md` — 오너 피드백 (→ 장성재)

- P95 1.54s, 에러율 0.00%로 SLO 달성 완료입니다. ✅
- 최대 3.03s가 SLO 경계(3,000ms)에 0.03s 초과한 케이스가 있습니다. `toss.api.read-timeout` 설정값과 Wiremock timeout 시나리오(5초 지연) 설정을 비교해 앱 타임아웃이 SLO보다 충분히 작게 잡혀있는지 확인 부탁드립니다. `앱 readTimeout < SLO(3s)` 조건이어야 P95 기준 여유가 생깁니다.
- mixed 시나리오(success 70% / timeout 10% / balance-error 10% / server-error 10%) 별도 실행으로 에러 유형별 응답시간 분포를 기록해 두면 PG 장애 대응 기준선이 됩니다.

### 피드백 반영 내용 (장성재)

> (장성재 작성) — 베이스라인에서 SLO 달성. 트러블슈팅 2건(h2c, bluegreen) 해결 후 재확인 결과 기록.

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

### 사전 준비

```bash
# Wiremock 기동 확인 (EC2-1에서)
docker ps --filter name=wiremock

# TOSS_API_BASE_URL 설정 확인 (EC2-1)
ACTIVE=$(cat /etc/fandrops/active-slot)
grep TOSS_API_BASE_URL /etc/fandrops/fandrops-prod.conf

# seed 주문 리셋 (재실행 시 — 결제 레코드 삭제 후 RESERVED 복원)
MYSQL="mysql -u fandrops_admin -pfandrops1234 -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"
$MYSQL -e "DELETE p FROM payment p INNER JOIN orders o ON p.order_id = o.id WHERE o.order_payment_key LIKE 'seed-opk-%';"
$MYSQL -e "UPDATE orders SET status='RESERVED', updated_at=NOW() WHERE order_payment_key LIKE 'seed-opk-%';"
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

BASE_URL=http://10.0.1.114:8081 k6 run \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  --out experimental-prometheus-rw \
  scenarios/03_payment_confirm.js
```

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 1,540ms ✅ | — | < 3,000ms | 미측정 |
| 평균 응답시간 | — | — | — | — |
| 에러율 | 0.00% ✅ | — | < 1% | 미측정 |
| 처리량 | — | — | — | — |

### 스크린샷

> `screenshots/tuned/s03_payment_confirm_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 05: SSE 대기열 연결 안정성 (SSE Queue)

**파일**: `infra/k6/scenarios/05_sse_queue.js`
**담당 오너**: 장성재, 지영재
**SLO**: 정상 구간 에러율 < 0.1%, 경계 구간 에러율 < 1%, 2,100 VU 초과 시 429 응답 필수
**실행 위치**: **GitHub Actions runner** (t3.small 메모리 초과 위험으로 EC2-2 사용 불가)

### 이전 피드백 (장성재, 지영재)

> 출처: `k6-baseline-results.md` — 오너 피드백 (→ 장성재, 지영재)

- normal_load 1,000 VU 구간부터 에러율 100%입니다. SSE 연결 자체가 성립하지 않는 상태로 SLO 달성 불가입니다. ❌
- 장성재: 429 응답 body에 `"retryable": true` 누락 — `api-contract.md` API 계약 위반입니다. 대기열 초과 응답 핸들러에 필드 추가 부탁드립니다.
- 지영재(자체): Nginx `worker_connections` · `ulimit -n` 실제 설정값 확인 및 SSE 동시 연결 허용 범위 점검 필요. 1,000 VU 정상 구간에서 429가 발생하는 원인이 Nginx 설정인지 앱 레벨 제한인지 구분이 선행돼야 합니다.

### 피드백 반영 내용 (장성재, 지영재)

**지영재 — 원인 조사 완료 (2026-06-20)**

EC2-1 SSM 접속으로 Nginx·OS 설정값 직접 확인:

| 항목 | 실측값 | 판정 |
|---|---|---|
| Nginx `worker_connections` | 4,096 | ✅ 무관 |
| OS `ulimit -n` | 65,535 | ✅ 무관 |
| Nginx master FD limit | 65,535 | ✅ 무관 |

**429 실제 원인**: `SseEmitterRegistry.registerOrReject()` — `emitters.size() >= 2000` 초과 시 `SseCapacityExceededException` → 429. k6는 SSE 연결을 브라우저처럼 유지하지 않고 첫 청크 수신 후 빠르게 반복 연결하는데, Spring은 클라이언트 disconnect를 `onTimeout`(60s) 전까지 감지하지 못해 stale emitter가 누적되어 2,000 한도를 초과함.

**해결 방향 (장성재 구현 필요)**: `SseEmitterRegistry`에 `sendHeartbeat()` 추가 — 5초 주기로 SSE comment 전송, `IOException` 발생 시 즉시 emitter 제거. `IllegalStateException`(이미 완료된 emitter에 write 시 발생)도 함께 catch 필요.

```java
// SseEmitterRegistry
public void sendHeartbeat() {
    for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
        try {
            entry.getValue().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(entry.getKey());
        }
    }
}

// QueueAdvanceScheduler
@Scheduled(fixedDelayString = "${fandrops.queue.scheduler.heartbeat-ms:5000}")
public void heartbeat() {
    registry.sendHeartbeat();
}
```

**장성재 — 피드백 반영 내용**

> (장성재 작성)

### 실행 방법

GitHub Actions → **Run k6 Load Test** → `scenario: 05` → `confirm: yes`

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| 정상 구간 에러율 (1,000 VU) | 100% ❌ | — | < 0.1% | 미측정 |
| 경계 구간 에러율 (1,800 VU) | 100% ❌ | — | < 1% | 미측정 |
| 초과 구간 429 발생 (2,100 VU) | 발생 ✅ | — | count > 0 | 미측정 |
| 429 retryable:true | 누락 ❌ | — | 필수 | 미측정 |

### 스크린샷

> `screenshots/tuned/s05_sse_queue_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 06: 통합 워크로드 모델 (Workload Model)

**파일**: `infra/k6/scenarios/06_workload_model.js`
**담당 오너**: 전체
**SLO**: Write P95 < 300ms, Read P95 < 120ms, 에러율 < 0.1%, 오버셀 0건

### 이전 피드백 (전체)

> 출처: `k6-baseline-results.md` — 오너 피드백 (전체)

- **정환철**: `GET /api/v1/artists/{id}/feeds` SecurityConfig에서 ROLE_FAN 허용 추가 필요.
- **장성재**: Wiremock stub에 임의 tossPaymentKey 패턴(REGEX) 추가 필요.
- **지영재**: `06_workload_model.js` payment check 조건에 429 허용 추가 (`r.status === 200 || r.status === 201 || r.status === 429`). queue join에도 rate limit 429 발생 여부 확인 후 필요 시 동일 수정.
- **형성빈**: `POST /api/v1/orders` (15%) 구간에서 `vuToken = null`로 인해 실제 HTTP 요청이 발생하지 않음. 재측정 전 vuToken 초기화 로직 추가 또는 tokens.csv 토큰으로 대체 필요.
- 블로커 2건 + check 조건 + vuToken 초기화 수정 후 재측정 예정.

### 피드백 반영 내용 (전체)

**지영재 — 완료 (2026-06-20)**

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| payment check 조건 | `200 \| 201` | `200 \| 201 \| 429` 추가 | k6 check |
| queue join check 조건 | `200 \| 201 \| 409` | `429` 추가 | k6 check |

> rate limit 정상 응답(429)을 check 실패로 집계하던 문제 수정. `06_workload_model.js` line 92·120.

**정환철, 장성재, 형성빈 — 피드백 반영 내용**

> (담당자 작성)

### 사전 준비

```bash
MYSQL="mysql -u fandrops_admin -pfandrops1234 -h fandrops-prod-mysql.coqwxjz7zumt.ap-northeast-2.rds.amazonaws.com fandrops"
REDIS_HOST="master.fandrops-prod-redis.q7gdno.apn2.cache.amazonaws.com"

$MYSQL -e "UPDATE inventory SET available_qty=200, reserved_qty=0, total_qty=200, version=0 WHERE product_id=1;"

for i in {1..2100}; do
  valkey-cli -h $REDIS_HOST --tls setex "access:ticket:1:$i" 86400 "test-ticket-token"
done

docker ps --filter name=wiremock
```

### 실행 명령어

```bash
cd /opt/fandrops/k6
export K6_PROMETHEUS_RW_SERVER_URL=http://10.0.1.114:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS="p(95),p(99)"

k6 run -e BASE_URL=https://api.fandrops.site \
  -e PRODUCT_ID=1 \
  -e ARTIST_ID=1 \
  -e ORDERS_JSON="$(cat seed/orders.json)" \
  --out experimental-prometheus-rw \
  scenarios/06_workload_model.js
```

### 결과 (튜닝 후)

| 지표 | 베이스라인 | 결과 | 목표 | 상태 |
|---|---|---|---|---|
| Write P95 | 측정 불가 (블로커) | — | < 300ms | 미측정 |
| Read P95 | 측정 불가 (블로커) | — | < 120ms | 미측정 |
| 에러율 | ~65% ❌ | — | < 0.1% | 미측정 |
| 오버셀 | 미확인 | — | 0건 | 미측정 |
| 총 iterations | ~544,907 | — | — | — |

### 스크린샷

> `screenshots/tuned/s06_workload_model_tuned.png`

### 관찰 및 오너 피드백

> 측정 후 작성

---

## SLO 달성 현황 요약

| 시나리오 | 베이스라인 P95 | 튜닝 후 P95 | 베이스라인 에러율 | 튜닝 후 에러율 | 오버셀 | SLO |
|---|---|---|---|---|---|---|
| 01 주문 동시성 | 1,750ms / 865ms(성공) | — | 75%\* | — | 0건 ✅ | 미측정 |
| 02 피드 Read | 133.02ms | — | 0.00% | — | — | 미측정 |
| 03 결제 확인 | 1,540ms ✅ | — | 0.00% ✅ | — | — | 미측정 |
| 04 드롭스 스파이크 | 266.24ms ✅ | — | 99.98%\* | — | 0건 ✅ | 미측정 |
| 05 SSE 대기열 | — | — | 100% ❌ | — | — | 미측정 |
| 06 통합 워크로드 | 측정 불가 | — | ~65% ❌ | — | — | 미측정 |