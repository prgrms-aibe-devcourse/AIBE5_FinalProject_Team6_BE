# k6 최종 측정 결과

> **목적**: 튜닝 피드백 반영 완료 후 전체 시나리오 SLO 최종 검증
> **실행 환경**: EC2-2 t3.small (k6 전용 러너) → EC2-1 Spring Boot (api.fandrops.site)
> **실행일**: 2026-06-
> **기준 SLO**: `docs/observability-metrics.md` 참고
> **이전 결과**: `docs/operations/k6-tuned-results.md`

---

## 테스트 환경

| 항목 | 값 |
|---|---|
| k6 실행 위치 | EC2-2 t3.small (서울 리전, Spring Boot 없음) |
| 측정 대상 | EC2-1 Spring Boot (t3.medium) — `https://api.fandrops.site` (VPC 내부 사설 IP) |
| 네트워크 | 동일 리전/VPC 내부 경로 기준, 외부 인터넷 왕복보다 변동성이 작음 |
| DB | RDS MySQL (별도 인스턴스) |
| Redis | ElastiCache (별도 인스턴스) |
| 모니터링 | Prometheus Remote Write → EC2-1 (`http://10.0.1.114:9090/api/v1/write`) |
| 토큰 | `/opt/fandrops/k6/seed/tokens.csv` — fan_id 1~2100 JWT |
| 적용 시나리오 | s01·s02·s03·s04·s05·s06·s07 전체 |

---

## SLO 목표 요약

| 유형 | P95 목표 | 에러율 목표 | 적용 시나리오 |
|---|---|---|---|
| Read | < 120ms | < 0.1% | 02, 07 |
| Write | < 300ms | < 0.1% | 01, 04, 06 |
| Payment | < 2,000ms | < 0.1% | 03 |
| SSE | 연결 거부 없음 (정상 구간) | — | 05 |

---

## 권장 실행 순서

| 순서 | 시나리오 | 사전 준비 | 실행 위치 |
|---|---|---|---|
| 1 | s02 피드 Read | 없음 | EC2-2 |
| 2 | s07 상품 조회 처리량 | 없음 | EC2-2 |
| 3 | s01 주문 동시성 | inventory 리셋(100) + Redis 티켓 재적재 | EC2-2 |
| 4 | s04 드롭스 스파이크 | inventory 리셋(100) + Redis 티켓 재적재 | EC2-2 |
| 5 | s03 결제 확인 | Wiremock 기동 확인 + `TOSS_API_READ_TIMEOUT=2s` 주입 | EC2-2 |
| 6 | s05 SSE 대기열 | — | **Actions runner** |
| 7 | s06 통합 워크로드 | inventory 리셋(200) + Wiremock 확인 | EC2-2 |

> ⚠️ **s05 먼저 실행 금지**: s05 실행 후 Redis 티켓이 UUID로 오염되어 s01·s04 전원 403 실패. 반드시 s04 이후 s05 실행.

---

## 시나리오 02: 피드 조회 Read P95

**파일**: `infra/k6/scenarios/02_feed_read.js`
**담당 오너**: 정환철
**SLO**: P95 < 120ms, 에러율 < 0.1%

### 목적

팬이 아티스트 피드를 조회할 때 Read SLO를 안정적으로 달성하는지 검증한다. 캐시 히트율이 안정화된 상태에서 cold start 구간 없이 P95가 120ms 이하를 유지하는지 확인한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 정환철 / 다음 측정 전 사전 피드백)

- `applyIsLiked()` 개선 반영 여부 확인: `likedFeedIds` Redis Set 캐싱 또는 `isLiked` viewer-specific 캐시 키 포함 구현이 완료됐는지 확인 후 측정. 미반영 시 캐시 히트 경로에서도 추가 DB 쿼리가 발생해 P95 168~172ms 수준 반복.
- cold start 워밍업 처리 방식 결정: k6 시나리오에 1분 warm-up 단계 추가 여부 또는 Grafana 집계에서 초반 250ms 피크 구간 제외 방식 팀 합의 필요.
- CPU 포화 해소 확인: 565 RPS 이상에서 CPU 100% 재현 여부 사전 점검.

### 피드백 반영 내용 (정환철)

> 작성 예정

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| | | | |

### 결과 (최종)

| 지표 | 튜닝 후 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 168~172ms ❌ | — | < 120ms | — |
| 에러율 | 0.00% ✅ | — | < 0.1% | — |
| 처리량(RPS) | 555~565/s | — | — | — |

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 정환철 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 07: 상품 조회 처리량 기준선 (Product Read)

**파일**: `infra/k6/scenarios/07_product_read.js`
**담당 오너**: 형성빈
**SLO**: P95 < 120ms, 에러율 < 0.1%

### 목적

팬이 드롭스 상품 목록을 조회하는 Read 엔드포인트의 처리량 기준선을 재검증한다. Redis 캐시 및 인덱스 적용 후 300 RPS에서 P95 120ms 이하를 달성하는지 확인한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)

- Redis 캐시 적용 완료 여부 확인: `product`, `product_image` Redis 캐시(TTL 120~300s) 구현이 반영됐는지 확인. 미반영 시 300 RPS × 3 쿼리 = 900 q/s DB 압박 구조 동일.
- `findRegularProducts` 인덱스 확인: `(drops_start_at, id)` 복합 인덱스 적용 여부 `EXPLAIN`으로 확인.
- dropped_iterations 모니터링: 이전 측정에서 251건 발생. 캐시 적용 후 꼬리 레이턴시(max 1.33s) 해소 여부 확인.

### 피드백 반영 내용 (형성빈)

> 작성 예정

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| | | | |

### 결과 (최종)

| 지표 | 튜닝 후 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 133.45ms ❌ | — | < 120ms | — |
| 에러율 | 0.00% ✅ | — | < 0.1% | — |
| 처리량(RPS) | ~298/s | — | 300 RPS | — |
| dropped_iterations | 251건 ⚠️ | — | ≈ 0 | — |

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 01: 주문 동시성 (Order Concurrency)

**파일**: `infra/k6/scenarios/01_order_concurrency.js`
**담당 오너**: 형성빈
**SLO**: `orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 목적

드롭스 오픈런 상황에서 200 VU 동시 발화 시 오버셀 없이 P95 300ms 이하를 달성하는지 검증한다. HikariCP 증설 및 낙관적 락 전환 후 row lock 경합 해소 여부를 확인한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)

- 인덱스 적용 여부 확인: `(product_id)` UNIQUE INDEX 존재 확인됨. 인덱스는 충분하나 단일 행 row lock 직렬화가 근본 원인.
- HikariCP pool size 증설 반영 여부: `maximumPoolSize` 증설(30) 반영됐는지 사전 확인.
- 낙관적 락 또는 분산 락 전환 여부: 코드 변경 없으면 P95 872ms 수준 반복 예상.
- 클린 DB 상태 필수: 이전 실행의 RESERVED 주문 및 seed 데이터 초기화 후 실행.

### 피드백 반영 내용 (형성빈)

> 작성 예정

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| | | | |

### 결과 (최종)

| 지표 | 튜닝 후 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 (전체) | 1,770ms ❌ | — | < 300ms | — |
| P95 응답시간 (성공 요청) | 872ms ❌ | — | < 300ms | — |
| 에러율 | 75%\* | — | < 0.1%\* | — |
| orders_reserved | 100건 ✅ | — | ≤ 100 | — |

> \* 에러율 75% = 300×409(DEPLETED) 정상 응답. checks_succeeded 기준 실제 오류 없음.

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 04: 드롭스 스파이크 (Drop Spike)

**파일**: `infra/k6/scenarios/04_drop_spike.js`
**담당 오너**: 형성빈
**SLO**: `spike_orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 목적

드롭스 오픈런 스파이크 트래픽(0→1,000 VU 30초)에서 Nginx rate limit, 대기열, 재고 차감이 정합성을 유지하는지 최종 검증한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)

- 클린 DB 상태 필수: RESERVED 주문 0건, 재고 초기화(`available_qty=100`) 확인 후 실행.
- Nginx rate limit 설정 유지 확인: `5r/s` rate limit이 변경되지 않았는지 사전 확인.
- s01 코드 변경 영향 여부: HikariCP pool size 증설 또는 락 전략 변경이 반영됐다면 s04에서도 P95 변화 확인 필요.

### 피드백 반영 내용 (형성빈)

> 작성 예정

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| | | | |

### 결과 (최종)

| 지표 | 튜닝 후 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 (전체) | 287.31ms ✅ | — | < 300ms | — |
| P95 응답시간 (성공 요청) | 252.84ms | — | — | — |
| 에러율 | 99.98%\* | — | < 0.1%\* | — |
| spike_orders_reserved | 100건 ✅ | — | ≤ 100 | — |

> \* 에러율 99.98% = Nginx 429 차단 정상 동작.

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 형성빈 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 03: 결제 확인 (Payment Confirm)

**파일**: `infra/k6/scenarios/03_payment_confirm.js`
**담당 오너**: 장성재
**SLO**: P95 < 2,000ms, 에러율 < 0.1%

### 목적

Toss PG 응답 지연·오류 상황에서도 중복 결제 없이 P95 2,000ms 이하로 처리되는지 최종 검증한다. `TOSS_API_READ_TIMEOUT=2s` 주입 상태에서 Wiremock wildcard 매핑으로 측정한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 장성재 / 다음 측정 전 사전 피드백)

- `TOSS_API_READ_TIMEOUT=2s` 주입 필수: EC2-1에서 환경변수 주입 후 서비스 재시작. 미주입 시 timeout 시나리오에서 max 응답시간 SLO 초과 재발.
- Wiremock 기동 확인: `docker ps --filter name=wiremock`으로 wildcard stub 상태 확인.
- 측정 완료 후 `TOSS_API_READ_TIMEOUT` 원복 필수: 실제 Toss API 호출에 영향 방지.
- mixed 시나리오 측정 여부 결정: 70%/10%/10%/10% 혼합 별도 측정 추가 가능.

### 피드백 반영 내용 (장성재)

#### @Transactional 분리 — PaymentConfirmTxHelper Bean 신설

**어떻게 반영했는지**
`PaymentConfirmService`의 단일 `@Transactional`을 제거하고, `PaymentConfirmTxHelper` Bean을 신설해 `precheck` / `applySuccess` / `applyFailure` 3개 메서드가 각자 독립 TX를 보유하도록 분리했습니다. Service는 TX 없이 오케스트레이션만 담당합니다.

**어떤 기술/방법을 적용했는지**
Spring AOP 프록시 기반 `@Transactional` — 같은 Bean 내 self-invocation은 프록시를 우회해 TX가 무시되는 문제를 별도 Bean 분리로 회피. `TossPaymentConfig`에서 수동 `@Bean` 등록으로 `txHelper → service` 주입 순서를 명시적으로 제어.

**어떻게 해결했는지**
PG HTTP 호출(최대 10s 대기)이 TX 범위 밖에 놓여 DB 커넥션 점유 시간이 제거됐습니다. 드롭스 오픈런 동시 요청 집중 시 HikariCP 커넥션 풀 고갈 위험을 차단.

---

#### 지적 1 — PrecheckResult / TxHelper public

**어떻게 반영했는지**
`PrecheckResult`와 `PaymentConfirmTxHelper` 모두 `public`으로 선언했습니다.

**어떤 기술/방법을 적용했는지**
Java 멀티모듈 접근 제어 — `payment-application` 타입을 `payment-infrastructure`에서 참조하려면 `public` 필수. package-private(기본값) 상태면 크로스 모듈 참조 시 컴파일 에러 발생.

**어떻게 해결했는지**
`TossPaymentConfig`(infrastructure)가 `PaymentConfirmTxHelper`(application)를 `@Bean`으로 등록할 때 두 타입 모두 `public`이므로 컴파일이 정상 통과됩니다.

---

#### 지적 2 — precheck-PG 타임 윈도 명시

**어떻게 반영했는지**
`PaymentConfirmService.confirm()` PG 호출 직전에 타임 윈도 설명 주석 3줄을 추가하고, `OptimisticLockingFailureException` catch 블록으로 실제 방어 로직을 함께 구현했습니다.

**어떤 기술/방법을 적용했는지**
JPA `@Version` 낙관적 락(Optimistic Locking) — precheck TX 커밋 후 PG 호출 전 사이에 다른 요청이 precheck를 통과하더라도, `applySuccess`에서 동일 Payment 엔티티 저장 시 `@Version` 불일치로 `OptimisticLockingFailureException`이 발생해 중복 처리를 차단.

**어떻게 해결했는지**
충돌 시 단순 throw 대신 `txHelper.precheck(command)` 재시도 → `isDone() == true`면 멱등 200 반환. 충돌 후에도 클라이언트 재시도 없이 정상 응답 보장. `PaymentConfirmServiceTest`에 `optimisticLockConflict_retriesPrecheck_returnsDone` 케이스로 검증됨.

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| TX 경계 | `PaymentConfirmService` 단일 `@Transactional` | `PaymentConfirmTxHelper` Bean 분리, 3개 메서드 독립 TX | Spring AOP 프록시, 별도 Bean |
| PG 호출 위치 | TX 내부 (DB 커넥션 점유 중 PG 대기) | TX 외부 | `@Transactional` 제거 |
| 멀티모듈 접근 | `PrecheckResult` package-private (컴파일 에러 위험) | `public` 선언 | Java visibility modifier |
| 동시성 방어 | 주석 없음 | precheck-PG 타임 윈도 주석 + `OptimisticLockingFailureException` catch 멱등 처리 | JPA `@Version` 낙관적 락 |

### 결과 (최종)

| 지표 | 튜닝 후 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 1,940ms ✅ | — | < 2,000ms | — |
| 에러율 | 0.00% ✅ | — | < 0.1% | — |
| 완료 iterations | 500/500 ✅ | — | 500 | — |

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 장성재 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 05: SSE 대기열 연결 안정성 (SSE Queue)

**파일**: `infra/k6/scenarios/05_sse_queue.js`
**담당 오너**: 장성재, 지영재
**SLO**: SSE 2,000 연결 수용 (5xx 없음), 초과 시 429 + `retryable:true` 응답 필수
**실행 위치**: **GitHub Actions runner**

### 목적

드롭스 오픈런 시 SSE 2,000 연결 수용 및 초과 구간 429 계약을 최종 검증한다. capacity_fill + overflow_probe 2단계 구조로 실행.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (→ 장성재 / 다음 측정 전 사전 피드백)

- capacity_fill 판정 기준: k6 summary `http_req_failed{capacity_fill}`은 구조상 항상 0/0. 실제 수용 성공 여부는 Nginx access log에서 200 응답 건수로 판정.
- overflow_probe dial timeout 허용: GHA runner 특성상 소수 `dial: i/o timeout` 발생 가능. `checks{scenario:overflow_probe} > 0.99` threshold 통과 여부로 판정.
- 실행 순서 준수: s04 이후 s05 실행 필수. 먼저 실행 시 Redis 티켓 오염으로 s01·s04 403 전원 실패.
- heartbeat 설정 확인: `fandrops.queue.scheduler.heartbeat-ms` 환경변수 배포 환경 적용 여부 사전 확인.

### 피드백 반영 내용 (장성재, 지영재)

#### heartbeat-ms 배포 환경 명시

**어떻게 반영했는지**
`application-prod.yml`과 `application-stg.yml` 두 배포 환경 yml에 `heartbeat-ms: 5000`을 명시적으로 추가했습니다.

**어떤 기술/방법을 적용했는지**
Spring `@Scheduled(fixedDelayString = "${fandrops.queue.scheduler.heartbeat-ms:5000}")` — 코드 기본값(`:5000`)에만 의존하던 상태를 yml에 명시해 환경별 설정으로 격상. 이후 값 조정이 필요할 때 코드 수정 없이 yml만 변경하면 됩니다.

**어떻게 해결했는지**
기본값 fallback 의존을 제거해 배포 환경에서 heartbeat 주기가 명확히 보장됩니다. 코드 기본값이 추후 변경되더라도 배포 환경은 yml 값(5000ms)을 따르므로 stale emitter 누적으로 인한 2,000 상한 조기 초과 재발 위험이 차단됩니다.

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| heartbeat 주기 설정 | yml 미설정 (코드 기본값 `:5000` fallback) | `application-prod.yml`, `application-stg.yml`에 `heartbeat-ms: 5000` 명시 | Spring `@Scheduled` SpEL 기본값 |

### 결과 (최종)

| 지표 | 튜닝 후 (17차) | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| SSE 2,000 연결 수용 | 2,000건 200 (Nginx log) ✅ | — | 5xx 없음 | — |
| 초과 구간 429 발생 | 299건 ✅ | — | count > 0 | — |
| 429 retryable:true | checks 99.33% ✅ | — | > 99% | — |
| 5xx 에러율 | 0% ✅ | — | < 0.1% | — |

> capacity_fill 200 성공은 k6 summary가 아닌 Nginx access log 기준.

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (→ 장성재, 지영재 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## 시나리오 06: 통합 워크로드 모델 (Workload Model)

**파일**: `infra/k6/scenarios/06_workload_model.js`
**담당 오너**: 전체
**SLO**: Write P95 < 300ms, Read P95 < 120ms, 에러율 < 0.1%, 오버셀 0건

### 목적

개별 시나리오(s01~s05)에서 발견되지 않는 시스템 전체 병목을 검증한다. 실제 트래픽 비율(피드 조회 60%, 대기열 진입 20%, 주문 15%, 결제 5%)을 반영한 혼합 부하로 전체 SLO를 한 번에 측정한다.

### 이전 피드백

> 출처: `k6-tuned-results.md` — 오너 피드백 (전체)

> 측정 후 작성

### 피드백 반영 내용

> 작성 예정

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| | | | |

### 결과 (최종)

| 지표 | 베이스라인 | 결과 (최종) | 목표 | 상태 |
|---|---|---|---|---|
| Write P95 | — | — | < 300ms | — |
| Read P95 | — | — | < 120ms | — |
| 에러율 | ~65% ❌ | — | < 0.1% | — |
| 오버셀 | — | — | 0건 | — |

### 스크린샷

> 측정 후 추가

### 문제 정의

> 측정 후 작성

### 관찰 및 오너 피드백

> 측정 후 작성

**오너 피드백 (전체 / 다음 측정 전 사전 피드백)**

> 측정 후 작성

### 개선 방향

> 측정 후 작성

---

## SLO 달성 현황 요약

| 시나리오 | 튜닝 후 P95 | 최종 측정 P95 | 튜닝 후 에러율 | 최종 에러율 | 오버셀 | SLO |
|---|---|---|---|---|---|---|
| 01 주문 동시성 | 872ms(성공) ❌ | — | 75%\* | — | — | — |
| 02 피드 Read | 168~172ms ❌ | — | 0.00% ✅ | — | — | — |
| 03 결제 확인 | 1,940ms ✅ | — | 0.00% ✅ | — | — | — |
| 04 드롭스 스파이크 | 287.31ms ✅ | — | 99.98%\* | — | 0건 ✅ | — |
| 05 SSE 대기열 | checks 99.33% ✅ | — | 0% ✅ | — | — | — |
| 06 통합 워크로드 | 미측정 | — | — | — | — | — |
| 07 상품 조회 처리량 | 133.45ms ❌ | — | 0.00% ✅ | — | — | — |
