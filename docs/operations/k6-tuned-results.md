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

| 항목 | 베이스라인 수치 | 이슈 |
|---|---|---|
| P95 응답시간 | 133.02ms | SLO 120ms 초과 |
| 쿼리 최적화 / 캐싱 | 미적용 | DB 직접 조회, 캐싱 없음 |

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

| 항목 | 베이스라인 수치 | 이슈 |
|---|---|---|
| P95 응답시간 (전체) | 1,750ms | SLO 300ms 초과 |
| P95 응답시간 (성공 요청) | 865ms | SLO 300ms 초과 |
| 에러율 | 75%\* | 재고 소진 후 409 — 정상 동작 |
| 오버셀 | 0건 | ✅ SLO 달성 |
| 분산 락 경합 | 미최적화 | 락 대기 누적으로 응답 지연 추정 |

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

| 항목 | 베이스라인 수치 | 이슈 |
|---|---|---|
| P95 응답시간 (전체) | 266.24ms | ✅ SLO 300ms 달성 |
| P95 응답시간 (성공 요청) | 640ms | 참고값 — SLO 기준 외 |
| 에러율 | 99.98%\* | 재고 소진 후 409 — 정상 동작 |
| 오버셀 | 0건 | ✅ SLO 달성 |

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

| 항목 | 베이스라인 수치 | 이슈 |
|---|---|---|
| P95 응답시간 | 1,540ms | ✅ SLO 3,000ms 달성 |
| 에러율 | 0.00% | ✅ SLO 달성 |
| h2c Upgrade 버그 | TossPaymentConfig HTTP_2 기본값 | Wiremock 빈 body → 403 체인 → PR #351 수정 완료 |
| bluegreen systemctl start no-op | start → restart 미변경 | 신규 JAR 미로드 → PR #353 수정 완료 |

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

| 항목 | 베이스라인 수치 | 이슈 |
|---|---|---|
| 정상 구간 에러율 (1,000 VU) | 100% | ❌ SLO 0.1% 초과 — SSE 연결 거부 로직 부재 |
| 경계 구간 에러율 (1,800 VU) | 100% | ❌ SLO 1% 초과 |
| 초과 구간 429 발생 (2,100 VU) | 발생 ✅ | — |
| 429 retryable:true | 누락 ❌ | 클라이언트 재시도 불가 — 응답 본문 수정 필요 |

### 피드백 반영 내용 (장성재, 지영재)

> (장성재, 지영재 작성)

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

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

| 항목 | 베이스라인 수치 | 이슈 | 담당 |
|---|---|---|---|
| Feed 403 ROLE_FAN | 60% 트래픽 100% 실패 | SecurityConfig에서 ROLE_FAN 접근 권한 누락 | 정환철 |
| Payment 400 Wiremock | 5% 트래픽 100% 실패 | tossPaymentKey 형식(`wl-N-N`) 스텁 미매칭 | 장성재 |
| Payment check 429 미허용 | check 실패 처리 | rate limiter 정상 429를 실패로 집계 | 지영재 |
| 전체 에러율 | ~65% | Feed+Payment 실패 합산 — http_req_failed 임계 초과 | 전체 |

### 피드백 반영 내용 (전체)

> (담당자 작성)

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| — | — | — | — |

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