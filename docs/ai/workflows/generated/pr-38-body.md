## 📝 작업 내용

- [x] `PaymentStatus` enum 추가 (`PENDING` / `SUCCESS` / `FAILED`)
- [x] `Payment` 도메인 객체 추가 — `orderId`(FK), `tossPaymentKey`, `amount`, `paymentMethod`, `status`, `paidAt`, `failedAt`
- [x] `PaymentRepository` 포트 인터페이스 추가 (domain 레이어, Spring/JPA import 없음)
- [x] `PaymentJpaEntity` 추가 — `order_id`(FK), `payment_key`(UNIQUE), `amount`, `method`
- [x] `PaymentJpaRepository`, `PaymentRepositoryAdapter` 추가
- [x] `PaymentTest` 단위 테스트 9개 추가

## 🧪 기술적 의사결정 및 검증

- **`order_id` FK vs `order_payment_key` String 선택:**  
  멀티모듈 모놀리스 + 단일 DB 구조에서 FK가 표준이며 참조 무결성을 보장. `orderPaymentKey`는 API 계약용 비즈니스 키로 ORDER 테이블에 두고, PAYMENT는 `order_id` FK로 ORDER를 참조.

- **`amount` PAYMENT 테이블 저장:**  
  `POST /payments/toss/confirm` 시 클라이언트가 보낸 `amount`와 서버 기록 값을 대조해야 금액 변조를 방지할 수 있음. ORDER 조인 방식은 타이밍 이슈와 불필요한 조인이 발생하므로 PAYMENT에 중복 저장.

- **데이터 기반 검증:** 도메인 단위 테스트 9개 전체 통과 (`BUILD SUCCESSFUL`)

- **트러블슈팅:**  
  초기 이슈 spec에 `orderPaymentKey`(String)와 ERD의 `order_id`(FK)가 불일치. ERD를 SSOT로 판단하여 JpaEntity를 `order_id` FK로 수정, ERD §3에 누락된 `amount` 컬럼 및 컬럼 목록 테이블 추가.

## 📌 주요 변경사항

- 추가: `Payment.java`, `PaymentStatus.java`, `PaymentRepository.java`
- 추가: `PaymentJpaEntity.java`, `PaymentJpaRepository.java`, `PaymentRepositoryAdapter.java`
- 추가: `PaymentTest.java`
- 수정: `payment-domain/build.gradle.kts` — testImplementation JUnit5 + AssertJ 추가
- 수정: `docs/erd/erd-design.md` §3 — PAYMENT 컬럼 목록 테이블 추가
- 수정: `docs/ai/personas/jangseongjae.md` — ERD 컬럼 목록 필수 포함 체크리스트 항목 추가

## 🔗 연관 이슈

- Closes #38
- Related to F06-01/F06-02 confirm API + 멱등성 + 타임아웃 Job (후행 이슈)

## ✅ 셀프 체크리스트

- [x] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [x] N+1 문제나 비효율적인 쿼리 실행 계획이 없는가?
- [ ] RestDocs 등 API 문서를 업데이트했는가? (API 변경 시) — 해당 없음 (스캐폴딩)
- [x] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가?
- [ ] 로컬 테스트 환경(Redis/MySQL) 정상 작동을 확인했는가? — 후행 이슈(confirm API) 구현 시 검증 예정
- [x] 소스코드 내 민감한 정보(API Key, 패스워드 등)가 제외되었는가?