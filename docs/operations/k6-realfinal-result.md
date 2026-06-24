# k6 Real Final 측정 결과

> **목적**: final 피드백 반영 완료 후 SLO 미달성 시나리오 재측정 및 최종 검증
> **실행 환경**: EC2-2 t3.small (k6 전용 러너) → EC2-1 Spring Boot (api.fandrops.site)
> **실행일**: 2026-06-
> **기준 SLO**: `docs/observability-metrics.md` 참고
> **이전 결과**: `docs/operations/k6-final-results.md`

---

## 재측정 정책

| 구분 | 시나리오 | 사유 |
|---|---|---|
| **재측정 대상** | s01 주문 동시성 (형성빈) | Final 기준 P95 872ms ❌ |
| **재측정 대상** | s02 피드 Read (정환철) | Final 기준 P95 168~172ms ❌ |
| **재측정 대상** | s03 결제 확인 (장성재) | 코드 개선 반영 (PR #447) 검증 |
| **이월** | s04 드롭스 스파이크 | Final 기준 P95 287.31ms ✅, 오버셀 0건 ✅ |
| **이월** | s05 SSE 대기열 | Final 기준 checks 99.33% ✅ |
| **이월** | s07 상품 조회 처리량 | 재측정 범위 외 (SLO 미달성 참고 수치 이월) |
| **대기** | s06 통합 워크로드 | s01 · s02 · s03 재측정 성공 후 진행 |

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

---

## SLO 목표 요약

| 유형 | P95 목표 | 에러율 목표 | 적용 시나리오 |
|---|---|---|---|
| Read | < 120ms | < 0.1% | 02, 07 |
| Write | < 300ms | < 0.1% | 01, 04, 06 |
| Payment | < 2,000ms | < 0.1% | 03 |
| SSE | 연결 거부 없음 (정상 구간) | — | 05 |

---

## 이월 시나리오

> **이월 기준**: `k6-final-results.md` 기준 SLO 달성. 코드 변경 없음. 재측정 생략.

### 시나리오 04: 드롭스 스파이크 — 이월

**SLO 달성 근거**: 튜닝 후 P95 287.31ms ✅, spike_orders_reserved 100건 ✅

| 지표 | 이월 결과 | 목표 | 상태 |
|---|---|---|---|
| P95 응답시간 (전체) | 287.31ms | < 300ms | ✅ |
| P95 응답시간 (성공 요청) | 252.84ms | — | — |
| spike_orders_reserved | 100건 | ≤ 100 | ✅ |
| 에러율 | 99.98%\* | < 0.1%\* | ✅ |

> \* 에러율 99.98% = Nginx 429 차단 정상 동작.

---

### 시나리오 05: SSE 대기열 연결 안정성 — 이월

**SLO 달성 근거**: SSE 2,000 연결 200 수용 ✅, 초과 429 retryable:true ✅

| 지표 | 이월 결과 | 목표 | 상태 |
|---|---|---|---|
| SSE 2,000 연결 수용 | 2,000건 200 (Nginx log) | 5xx 없음 | ✅ |
| 초과 구간 429 발생 | 299건 | count > 0 | ✅ |
| 429 retryable:true | checks 99.33% | > 99% | ✅ |
| 5xx 에러율 | 0% | < 0.1% | ✅ |

> capacity_fill 200 성공은 k6 summary가 아닌 Nginx access log 기준.

---

### 시나리오 07: 상품 조회 처리량 — 이월 (SLO 미달성, 재측정 범위 외)

> s07은 이번 real final 재측정 대상에 포함되지 않습니다. 개선 예정 항목(복합 인덱스, Redis 캐시)은 추후 별도 작업으로 반영 예정이며, 튜닝 후 결과를 참고 수치로 이월합니다.

| 지표 | 이월 결과 (튜닝 후) | 목표 | 상태 |
|---|---|---|---|
| P95 응답시간 | 133.45ms | < 120ms | ❌ |
| 에러율 | 0.00% | < 0.1% | ✅ |
| 처리량(RPS) | ~298/s | 300 RPS | — |

---

## 재측정 시나리오

---

## 시나리오 01: 주문 동시성 (Order Concurrency)

**파일**: `infra/k6/scenarios/01_order_concurrency.js`
**담당 오너**: 형성빈
**SLO**: `orders_reserved ≤ 100` (오버셀 0건), P95 < 300ms, 에러율 < 0.1%

### 목적

드롭스 오픈런 상황에서 200 VU 동시 발화 시 오버셀 없이 P95 300ms 이하를 달성하는지 재검증한다. HikariCP 증설 및 낙관적 락 전환 반영 후 row lock 경합 해소를 확인한다.

### 이전 피드백

> 출처: `k6-final-results.md` — 피드백 반영 내용 (형성빈)

- HikariCP pool size 증설: `maximumPoolSize: 30` 설정 반영 여부 확인.
- 낙관적 락 전환: `inventory.version` 컬럼 활용, `@Version` 적용 여부 확인.
- 클린 DB 상태 필수: RESERVED 주문 0건, 재고 초기화(`available_qty=100`) 확인 후 실행.

### 피드백 반영 내용 (형성빈)

> 재측정 전 형성빈이 작성

#### HikariCP 풀 크기 증설

**어떻게 반영했는지**

> 작성 예정

**어떤 기술/방법을 적용했는지**

> 작성 예정

**어떻게 해결했는지**

> 작성 예정

---

#### 낙관적 락 전환

**어떻게 반영했는지**

> 작성 예정

**어떤 기술/방법을 적용했는지**

> 작성 예정

**어떻게 해결했는지**

> 작성 예정

---

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| HikariCP 풀 크기 | 기본값 10 (yml 미설정) | `maximumPoolSize: 30` | HikariCP |
| 락 전략 | 단일 행 row lock 직렬화 | `@Version` 낙관적 락 전환 | JPA Optimistic Lock |

### 결과 (Real Final)

| 지표 | Final 기준 | 결과 (Real Final) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 (전체) | 1,770ms ❌ | — | < 300ms | — |
| P95 응답시간 (성공 요청) | 872ms ❌ | — | < 300ms | — |
| 에러율 | 75%\* | — | < 0.1%\* | — |
| orders_reserved | 100건 ✅ | — | ≤ 100 | — |

> \* 에러율 75% = 300×409(DEPLETED) 정상 응답. checks_succeeded 기준 실제 오류 없음.

### 스크린샷

> 측정 후 추가

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 02: 피드 조회 Read P95

**파일**: `infra/k6/scenarios/02_feed_read.js`
**담당 오너**: 정환철
**SLO**: P95 < 120ms, 에러율 < 0.1%

### 목적

팬이 아티스트 피드를 조회할 때 Read SLO를 안정적으로 달성하는지 재검증한다. N+1 제거, 인덱스 추가, Redis 캐시 반영 후 P95 120ms 이하 달성 여부를 확인한다.

### 이전 피드백

> 출처: `k6-final-results.md` — 피드백 반영 내용 (정환철)

- `applyIsLiked()` 개선 반영 여부: viewer-specific 캐시 키 + SingleFlight 적용 완료 여부 확인.
- cold start 워밍업 처리: 1분 warm-up 단계 추가 여부 또는 Grafana 집계 초반 구간 제외 방식 합의 후 측정.
- CPU 포화 해소: 565 RPS 이상에서 CPU 100% 재현 여부 사전 점검.

### 피드백 반영 내용 (정환철)

> 재측정 전 정환철이 작성

#### N+1 쿼리 제거 및 인덱스 추가

**어떻게 반영했는지**

> 작성 예정

**어떤 기술/방법을 적용했는지**

> 작성 예정

**어떻게 해결했는지**

> 작성 예정

---

#### Redis 캐시 적용

**어떻게 반영했는지**

> 작성 예정

**어떤 기술/방법을 적용했는지**

> 작성 예정

**어떻게 해결했는지**

> 작성 예정

---

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| 이미지·좋아요 조회 | 피드 수 × 개별 쿼리 (N+1) | bulk IN 쿼리 (`findByFeedIdIn`, `findLikedFeedIdsByFanId`) | JPA IN 쿼리 |
| 대댓글 조회 | 댓글 수 × 개별 쿼리 | `findRepliesByParentIds` bulk 조회 | JPA IN 쿼리 |
| 커서 인덱스 | `artist_id` 단일 인덱스 | `idx_artist_feed_artist_cursor (artist_id, id DESC)` 추가 | DB 인덱스 |
| 캐시 레이어 | 없음 | Redis TTL 60s + jitter + SingleFlight | Redis, ConcurrentHashMap |

### 결과 (Real Final)

| 지표 | Final 기준 | 결과 (Real Final) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 168~172ms ❌ | — | < 120ms | — |
| 에러율 | 0.00% ✅ | — | < 0.1% | — |
| 처리량(RPS) | 555~565/s | — | — | — |

### 스크린샷

> 측정 후 추가

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 03: 결제 확인 (Payment Confirm)

**파일**: `infra/k6/scenarios/03_payment_confirm.js`
**담당 오너**: 장성재
**SLO**: P95 < 2,000ms, 에러율 < 0.1%

### 목적

Toss PG 응답 지연·오류 상황에서도 중복 결제 없이 P95 2,000ms 이하로 처리되는지 재검증한다. `@Transactional` 분리 및 낙관적 락 적용 (PR #447) 후 HikariCP 커넥션 점유 해소 및 동시성 방어를 확인한다. `TOSS_API_READ_TIMEOUT=2s` 주입 상태에서 Wiremock wildcard 매핑으로 측정한다.

### 이전 피드백

> 출처: `k6-final-results.md` — 오너 피드백 (→ 장성재)

- `TOSS_API_READ_TIMEOUT=2s` 주입 필수: EC2-1에서 환경변수 주입 후 서비스 재시작. 미주입 시 timeout 시나리오에서 max 응답시간 SLO 초과 재발.
- Wiremock 기동 확인: `docker ps --filter name=wiremock`으로 wildcard stub 상태 확인.
- 측정 완료 후 `TOSS_API_READ_TIMEOUT` 원복 필수: 실제 Toss API 호출에 영향 방지.

### 피드백 반영 내용 (장성재)

> **PR #447** 반영 내용

#### @Transactional 분리 — PaymentConfirmTxHelper Bean 신설

**어떻게 반영했는지**
`PaymentConfirmService`의 단일 `@Transactional`을 제거하고, `PaymentConfirmTxHelper` Bean을 신설해 `precheck` / `applySuccess` / `applyFailure` 3개 메서드가 각자 독립 TX를 보유하도록 분리했습니다. Service는 TX 없이 오케스트레이션만 담당합니다.

**어떤 기술/방법을 적용했는지**
Spring AOP 프록시 기반 `@Transactional` — 같은 Bean 내 self-invocation은 프록시를 우회해 TX가 무시되는 문제를 별도 Bean 분리로 회피. `TossPaymentConfig`에서 수동 `@Bean` 등록으로 `txHelper → service` 주입 순서를 명시적으로 제어.

**어떻게 해결했는지**
PG HTTP 호출(최대 10s 대기)이 TX 범위 밖에 놓여 DB 커넥션 점유 시간이 제거됐습니다. 드롭스 오픈런 동시 요청 집중 시 HikariCP 커넥션 풀 고갈 위험을 차단.

---

#### PrecheckResult / TxHelper public 선언

**어떻게 반영했는지**
`PrecheckResult`와 `PaymentConfirmTxHelper` 모두 `public`으로 선언했습니다.

**어떤 기술/방법을 적용했는지**
Java 멀티모듈 접근 제어 — `payment-application` 타입을 `payment-infrastructure`에서 참조하려면 `public` 필수. package-private(기본값) 상태면 크로스 모듈 참조 시 컴파일 에러 발생.

**어떻게 해결했는지**
`TossPaymentConfig`(infrastructure)가 `PaymentConfirmTxHelper`(application)를 `@Bean`으로 등록할 때 두 타입 모두 `public`이므로 컴파일이 정상 통과됩니다.

---

#### precheck-PG 타임 윈도 명시 및 낙관적 락

**어떻게 반영했는지**
`PaymentConfirmService.confirm()` PG 호출 직전에 타임 윈도 설명 주석 3줄을 추가하고, `OptimisticLockingFailureException` catch 블록으로 실제 방어 로직을 함께 구현했습니다.

**어떤 기술/방법을 적용했는지**
JPA `@Version` 낙관적 락(Optimistic Locking) — precheck TX 커밋 후 PG 호출 전 사이에 다른 요청이 precheck를 통과하더라도, `applySuccess`에서 동일 Payment 엔티티 저장 시 `@Version` 불일치로 `OptimisticLockingFailureException`이 발생해 중복 처리를 차단.

**어떻게 해결했는지**
충돌 시 단순 throw 대신 `txHelper.precheck(command)` 재시도 → `isDone() == true`면 멱등 200 반환. 충돌 후에도 클라이언트 재시도 없이 정상 응답 보장. `PaymentConfirmServiceTest`에 `optimisticLockConflict_retriesPrecheck_returnsDone` 케이스로 검증됨.

---

| 항목 | 변경 전 | 변경 내용 | 적용 기술 |
|---|---|---|---|
| TX 경계 | `PaymentConfirmService` 단일 `@Transactional` | `PaymentConfirmTxHelper` Bean 분리, 3개 메서드 독립 TX | Spring AOP 프록시, 별도 Bean |
| PG 호출 위치 | TX 내부 (DB 커넥션 점유 중 PG 대기) | TX 외부 | `@Transactional` 제거 |
| 멀티모듈 접근 | `PrecheckResult` package-private (컴파일 에러 위험) | `public` 선언 | Java visibility modifier |
| 동시성 방어 | 주석 없음 | precheck-PG 타임 윈도 주석 + `OptimisticLockingFailureException` catch 멱등 처리 | JPA `@Version` 낙관적 락 |

### 결과 (Real Final)

| 지표 | Final 기준 | 결과 (Real Final) | 목표 | 상태 |
|---|---|---|---|---|
| P95 응답시간 | 1,940ms ✅ | — | < 2,000ms | — |
| 에러율 | 0.00% ✅ | — | < 0.1% | — |
| 완료 iterations | 500/500 ✅ | — | 500 | — |

### 스크린샷

> 측정 후 추가

### 관찰 및 오너 피드백

> 측정 후 작성

---

## 시나리오 06: 통합 워크로드 모델 (대기)

**파일**: `infra/k6/scenarios/06_workload_model.js`
**담당 오너**: 전체
**SLO**: Write P95 < 300ms, Read P95 < 120ms, 에러율 < 0.1%, 오버셀 0건

### 진행 조건

> s01 · s02 · s03 재측정 SLO 달성 후 진행.

| 선행 조건 | 상태 |
|---|---|
| s01 주문 동시성 SLO 달성 | — |
| s02 피드 Read SLO 달성 | — |
| s03 결제 확인 재검증 완료 | — |

### 목적

개별 시나리오(s01~s05)에서 발견되지 않는 시스템 전체 병목을 검증한다. 실제 트래픽 비율(피드 조회 60%, 대기열 진입 20%, 주문 15%, 결제 5%)을 반영한 혼합 부하로 전체 SLO를 한 번에 측정한다.

### 사전 준비

- inventory 리셋(200) + Wiremock 확인
- RESERVED 주문 0건 확인

### 결과 (Real Final)

| 지표 | 결과 (Real Final) | 목표 | 상태 |
|---|---|---|---|
| Write P95 | — | < 300ms | — |
| Read P95 | — | < 120ms | — |
| 에러율 | — | < 0.1% | — |
| 오버셀 | — | 0건 | — |

### 스크린샷

> 측정 후 추가

### 관찰 및 오너 피드백

> 측정 후 작성

---

## SLO 달성 현황 요약

| 시나리오 | Final 기준 P95 | Real Final P95 | Final 에러율 | Real Final 에러율 | 오버셀 | SLO |
|---|---|---|---|---|---|---|
| 01 주문 동시성 | 872ms(성공) ❌ | — | 75%\* | — | — | — |
| 02 피드 Read | 168~172ms ❌ | — | 0.00% ✅ | — | — | — |
| 03 결제 확인 | 1,940ms ✅ | — | 0.00% ✅ | — | — | — |
| 04 드롭스 스파이크 | 287.31ms ✅ **이월** | — | 99.98%\* ✅ | — | 0건 ✅ | ✅ |
| 05 SSE 대기열 | checks 99.33% ✅ **이월** | — | 0% ✅ | — | — | ✅ |
| 06 통합 워크로드 | 미측정 | — | — | — | — | 대기 |
| 07 상품 조회 처리량 | 133.45ms ❌ **이월** | — | 0.00% ✅ | — | — | ❌ |