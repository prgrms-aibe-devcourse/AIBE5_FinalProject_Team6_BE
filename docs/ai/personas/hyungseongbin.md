# Persona — 형성빈 (Order · Inventory · Commerce)

**선행 (매 세션):** [`../SHARED.md`](../SHARED.md) — Cursor: `@docs/ai/SHARED.md` + 본 파일 · Claude Code: 루트 `CLAUDE.md` + SHARED 읽기 + `@` 본 파일.

---

## 역할 한 줄

`order` · `inventory` — 상품, **주문**, 재고 예약/확정/복구, 핫딜 동시성, 재입고 이벤트 **발행**.

## 수정 가능 경로

```
modules/order/**
modules/inventory/**
```

## 손대지 말 것 (기본)

`modules/payment/**` (웹훅·PG), `modules/user/**` (대기열 구현체)  
`payment` ↔ `order` 연동은 **application 포트** + 장성재와 스키마 합의.

---

## 추가 필수 참조 (@로드)

| 문서 | 언제 |
| --- | --- |
| `docs/api/mvp-api-spec.md` | § Product, § Order, § Inventory Internal |
| `docs/state/invariants-and-state-machines.md` | **전체** (ORDER·재고·타임아웃·Saga 소비) |
| `docs/sequence/payment-flow-reason.md` | 재고 실패·PENDING→CANCELLED |
| `docs/erd/erd-design.md` | § PRODUCT reserved_quantity, § ORDER status |
| `docs/operations/failure-policy.md` | DB 고갈, RESERVE_FAILED |

---

## 동시 갱신 문서 (해당 시)

| 변경 | 갱신 |
| --- | --- |
| 주문 API·상태 | `mvp-api-spec.md`, `invariants-and-state-machines.md` |
| `OUT_OF_STOCK` vs `RESERVE_FAILED` | `api-contract.md` |
| 재고 포트 시그니처 | `mvp-api-spec.md` § Inventory Internal |

---

## 협업 · PR 리뷰어

| 주제 | 리뷰 요청 |
| --- | --- |
| accessTicket 검증 (OrderService) | 표지민 |
| 결제 후 `PAID→COMPLETED`, 웹훅 | 장성재 |
| `inventory.restore` / `confirm` 호출 순서 | 장성재 |
| 상품 목록 캐시·부하 | 지영재 |

---

## 김최고 체크리스트 (형성빈)

- [ ] `POST /orders` = 주문 + reserve **단일 TX**, 응답 `RESERVED` + `orderPaymentKey`
- [ ] `OUT_OF_STOCK`(전체 품절) ≠ `RESERVE_FAILED`(경쟁 패배, retryable)
- [ ] `RESERVED`만 재고 선점 의미 — 실패 시 `PENDING→CANCELLED` (RESERVED 금지)
- [ ] MVP 재고 락: MySQL `FOR UPDATE` (Redis 락은 Phase 3)
- [ ] `inventory` 포트: reserve / confirm / restore — HTTP 아님

---

## 로컬·테스트

```bash
./gradlew :modules:order:order-domain:test :modules:inventory:inventory-application:test
./gradlew clean build
```
