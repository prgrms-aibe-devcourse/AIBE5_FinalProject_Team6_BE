# Persona — 장성재 (Payment · Integration)

**선행 (매 세션):** [`../SHARED.md`](../SHARED.md) — Cursor: `@docs/ai/SHARED.md` + 본 파일 · Claude Code: 루트 `CLAUDE.md` + SHARED 읽기 + `@` 본 파일.

---

## 역할 한 줄

`payment` — 토스 PG, confirm, **웹훅(Webhook Receiver)**, 멱등, 결제 후 **주문·재고 E2E**, Saga 보상 orchestration.

## 수정 가능 경로

```
modules/payment/**
```

## 손대지 말 것 (기본)

`modules/order/**` · `modules/inventory/**` **구현체 직접 수정** — `OrderStatePort`, `InventoryConfirmPort` 등 **호출만**.  
주문 상태 enum 변경은 형성빈과 **동시 PR**.

---

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/mvp-api-spec.md` | § 결제 식별자, § Order/Payment, webhook |
| `docs/state/invariants-and-state-machines.md` | §3 PAYMENT · §5 Saga · §4 타임아웃 |
| `docs/sequence/payment-flow-reason.md` | **전체** |
| `docs/erd/erd-design.md` | §3 PAYMENT (`payment_key`, `failed_at`) |
| `docs/erd/data-retention-and-audit-policy.md` | webhook 90일, audit |
| `docs/operations/failure-policy.md` | timeout, 웹훅 중복 |

---

## 동시 갱신 문서 (해당 시)

| 변경 | 갱신 |
| --- | --- |
| confirm / webhook 동작 | `mvp-api-spec.md`, `payment-flow-reason.md` |
| `tossPaymentKey` 멱등 | `erd-design.md` §3 · `api-contract.md` |

---

## 협업 · PR 리뷰어

| 주제 | 리뷰 요청 |
| --- | --- |
| ORDER 상태 전이 규칙 | 형성빈 |
| inventory confirm/restore | 형성빈 |
| 결제 완료 알림 | 표지민 |
| 웹훅 엔드포인트·TLS·Nginx | 지영재 |

---

## 김최고 체크리스트 (장성재)

- [ ] `tossPaymentKey` → DB `payment_key` **Unique** 멱등
- [ ] `RESERVED→PAID→COMPLETED` / 실패 `RESERVED→FAILED→CANCELLED` — **클라이언트 API 없음**
- [ ] `FAILED`는 Transient — 반드시 `CANCELLED` 수렴
- [ ] confirm timeout 시 주문은 `RESERVED` 유지, **15분** Job — `invariants-and-state-machines.md` §4.1
- [ ] PaymentService = **Webhook Receiver** (동기 PG 호출만이 전부가 아님)

---

## 로컬·테스트

```bash
./gradlew :modules:payment:payment-domain:test :modules:payment:payment-application:test
# 웹훅 E2E: Testcontainers + 토스 샌드박스 (팀 픽스처)
```
