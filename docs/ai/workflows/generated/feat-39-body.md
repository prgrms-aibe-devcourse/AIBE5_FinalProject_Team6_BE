## 📝 작업 내용

- [x] `POST /api/v1/payments/toss/confirm` — Toss PG 동기 confirm API (F06-01)
- [x] 결제 멱등성 — `tossPaymentKey` DB UNIQUE + `@Version` 낙관적 락 (ADR-006)
- [x] `PaymentTimeoutJob` — PENDING 결제 15분 타임아웃 → `PaymentFailedEvent` 발행 (`@Scheduled` 60s)
- [x] `PaymentTimeoutItemProcessor` — `@Transactional(REQUIRES_NEW)` 항목 격리
- [x] 보상 트랜잭션 이벤트화 — `PaymentApprovedEvent` / `PaymentFailedEvent` 발행, `OrderStatePort`·`InventoryRestorePort` 직접 호출 제거
- [x] Toss PG 4xx/5xx 분기 — 401/403/5xx → `TossPaymentUnavailableException` (503 retryable), 비즈니스 거절만 `FAILED` 전이
- [x] RestClient connect 3s / read 10s timeout 설정
- [x] `@Valid` + Bean Validation(`@NotNull`·`@NotBlank`·`@Positive`) 입력 검증
- [x] amount 변조 검증 — PG 호출 전 요청 금액 ≠ 저장 금액 시 400
- [x] `application-local.yml` gitignore 처리 + `application-local.example.yml` 전체 템플릿화
- [x] ADR-006 작성 — 멱등성 구현 방식 3안 비교·근거

## 🧪 기술적 의사결정 및 검증

- **멱등성 — A안(낙관적 락 + DB UNIQUE) 선택 (ADR-006):**
  B안(Redis 분산락)은 ADR-001 Redis 용도 한정 원칙 위반·가용성 의존 추가. C안(SELECT FOR UPDATE)은 드롭스 오픈런 시 커넥션 풀 고갈 위험. Toss PG가 `tossPaymentKey` 기준 멱등을 자체 보장하므로 DB UNIQUE 제약이 최후 방어선으로 충분.

- **보상 트랜잭션 이벤트화:**
  `OrderStatePort.cancel()` + `InventoryRestorePort.restore()` 직접 호출 → `PaymentFailedEvent` 발행으로 전환. order 모듈 `@TransactionalEventListener(AFTER_COMMIT)` 수신으로 결합도 제거. payment 모듈이 order 내부 상태 전이 단계를 알 필요 없음.

- **트러블슈팅 — `@Transactional` 롤백 버그:**
  `PaymentConfirmFailedException extends RuntimeException` 이므로 `@Transactional` 기본 동작(unchecked → rollback)에 의해 `payment.fail()` 저장이 취소되는 버그 발견. `noRollbackFor = PaymentConfirmFailedException.class` 추가로 PG 거절 시에도 PAYMENT FAILED 상태가 DB에 커밋되도록 수정.

## 📌 주요 변경사항

- 추가: `PaymentController`, `PaymentTimeoutJob`, `PaymentControllerAdvice`, `PaymentConfirmRequest/Response`
- 추가: `PaymentConfirmService`, `PaymentTimeoutService`, `PaymentTimeoutItemProcessor`
- 추가: `PaymentApprovedEvent`, `PaymentFailedEvent`
- 추가: `TossPaymentGatewayAdapter`, `TossPaymentConfig`, `TossProperties`, `TossPaymentUnavailableException`
- 추가: `docs/adr/ADR-006-payment-confirm-idempotency.md`
- 수정: `Payment.java` — `createdAt(Instant)` 필드 추가 (타임아웃 Job 기준)
- 수정: `PaymentRepository` — `findPendingOlderThan(Instant)` 추가
- 수정: `PaymentJpaEntity` — `created_at` 컬럼, `@Version`
- 수정: `FandropsApplication` — `@EnableScheduling`
- 수정: `.gitignore` — `application-local.yml` 추가
- 수정: `application-local.example.yml` — 전체 로컬 설정 템플릿
- 수정: `docs/erd/erd-design.md` — §3 PAYMENT `created_at` 컬럼 동기화
- 수정: `docs/ai/personas/jangseongjae.md` — 이벤트 기반 아키텍처 반영
- 삭제: `OrderStatePort`, `InventoryRestorePort`, `StubOrderStateAdapter`, `StubInventoryRestoreAdapter`

## 🔗 연관 이슈

- Closes #39
- Related to #32 (형성빈 `POST /orders` — `PaymentApprovedEvent`/`PaymentFailedEvent` 리스너 구현 필요)

## ✅ 셀프 체크리스트

- [x] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가?
- [ ] RestDocs 등 API 문서를 업데이트했는가? (API 변경 시) — 웹훅 F06-03 PR에서 통합 예정
- [x] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가?
- [x] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가?
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제외되었는가?