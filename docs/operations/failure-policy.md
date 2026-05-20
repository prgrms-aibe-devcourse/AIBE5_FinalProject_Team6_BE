# 장애 · 복구 정책 (Failure Policy)

> **관련:** [API 계약](../api/api-contract.md) · [상태 머신](../state/invariants-and-state-machines.md) · [결제 시퀀스](../sequence/payment-flow-reason.md) · [데이터 보관·Audit](../erd/data-retention-and-audit-policy.md) · [ADR-001](../adr/ADR-001-multi-module-monolith.md)

K-Pop **오픈런(드롭스)** 환경에서 외부 의존성(Redis, PG, DB) 장애 시 **정합성 우선** 동작을 정의한다.  
목표: 오버셀·중복 결제 **0건** 유지, 장애 시 **fail-fast**로 피해 반경 축소.

---

## 1. 의존성별 장애 정책

| 상황 | 정책 (MVP) | 사용자 / 시스템 영향 | 담당 |
| --- | --- | --- | --- |
| **Redis 다운** | **Fail-fast**: 대기열·RateLimit 비활성 + Nginx/ALB **트래픽 제한** 강화 | `503` 또는 `RATE_LIMITED` 유사 메시지 — *「일시적으로 입장이 제한됩니다」* | 장성재 · 지영재 |
| **Outbox Worker 중단** (MVP: DB Outbox, **Kafka Not Scope**) | **Outbox 적재는 지속**, consumer 재기동 시 **미발행 건 재전송** | 결제·알림 **확정 지연** 가능. `GET /orders/{id}` 등 **조회는 가능** | 표지민 · 장성재 |
| **토스 결제 API timeout** | 주문 `ORDER.status` = **`RESERVED`** 유지 ([§2](#2-결제-대기-상태-명칭)) · **15분** 후 [만료 Job](../state/invariants-and-state-machines.md#41-reserved-결제-타임아웃-상세) | *「결제 확인 중입니다」* · 만료 시 *「결제 시간이 초과되어 주문이 취소되었습니다」* (`retryable: false`) | 장성재 |
| **웹훅 중복** | `tossPaymentKey` (**idempotency**) 로 무해화 — [P-1](../state/invariants-and-state-machines.md#33-payment-불변조건) | 중복 결제·이중 재고 차감 **방지** | 장성재 |
| **RateLimit 오탐·과차단** | 결제/주문 진입 보호 정책은 장성재가 조정, Nginx/ALB 임계값은 지영재가 반영 | 정상 팬 주문 진입 지연 가능. `429 RATE_LIMITED` 비율로 탐지 | 장성재 · 지영재 |
| **DB 커넥션 고갈** | 대기열 **입장 제한** 강화 · 일부 write `429` `RATE_LIMITED` | 드롭스 **공정성** 유지(선착순 붕괴 방지) | 지영재 · 형성빈 |
| **캐시 miss 폭증** | **SingleFlight** + TTL **jitter** | DB 보호, 조회 P95 악화 완화 | 정환철 · 형성빈 |
| **MySQL primary 불가** | **Fail-fast** — 주문·결제 write 중단, read-only(가능 시) 또는 503 | 전면 장애 — [P0](./incident-response.md#2-장애-등급) | 지영재 |

### Phase 2 (참고)

| 상황 | 정책 |
| --- | --- |
| Kafka/RabbitMQ 다운 | 브로커 도입 시: Outbox → broker 적재 지속, consumer lag 모니터링 |

---

## 2. 결제 대기 상태 명칭

Notion 초안의 `PAYMENT_PENDING`은 본 프로젝트 **ORDER.status = `RESERVED`** 와 동일 의미(재고 선점 완료, PG 결제 대기)이다.

| 문서/표현 | DB 상태 |
| --- | --- |
| PAYMENT_PENDING (기획 표) | `RESERVED` |
| 결제 승인 후 | `PAID` → `COMPLETED` |

타임아웃·Saga는 [상태 머신 §4](../state/invariants-and-state-machines.md#4-타임아웃-정책) SSOT.

---

## 3. 시나리오별 상세

### 3.1 Redis 다운

```
Health: Redis UNAVAILABLE
  → 대기열 join/SSE: 즉시 503 + fail-fast (폴백으로 DB 대기열 쓰지 않음 — MVP)
  → RateLimit: in-memory 제한적 방어 또는 Nginx limit_req (정책 오너 장성재, 운영 오너 지영재)
  → Alert: P1 (incident-response)
```

`local` 프로필은 Redis 미사용([application-local.yml](../../apps/api-server/src/main/resources/application-local.yml)) — **prod/stg 정책만 적용**.

### 3.2 Outbox · 알림 지연

[ADR-001](../adr/ADR-001-multi-module-monolith.md): **Domain Event + DB `outbox_events`**. Kafka 없음.

| 단계 | 동작 |
| --- | --- |
| 장애 중 | 도메인 TX 커밋 + outbox INSERT **성공**까지는 동기 |
| 복구 후 | `OutboxPublisher`가 `pending` 건 재전송 · [30일 purge](../erd/data-retention-and-audit-policy.md#23-대기열--알림-장성재--표지민) |
| 모니터링 | `outbox_pending_count` > 임계 → P1 |

결제 **웹훅 처리**는 Outbox와 **별도 동기 경로**(Payment Webhook Receiver) — 웹훅 실패 시 [FAILED→CANCELLED Saga](../state/invariants-and-state-machines.md#51-결제-실패-reserved--failed--cancelled).

### 3.3 결제 API timeout · PG 지연

| 항목 | 정책 |
| --- | --- |
| confirm API timeout | 클라이언트에 `retryable: true` + *「결제 확인 중」* (짧은 재조회 `GET /orders/{id}`) |
| 15분 초과 | `RESERVED` → `FAILED` → `CANCELLED` + 재고 restore |
| PAID 후처리 지연 | 60초 SLO · Job 재시도 — [§4.3 상태 머신](../state/invariants-and-state-machines.md#43-paid-정체-재처리) |

API 응답 예: [api-contract § PAYMENT_FAILED / retryable](../api/api-contract.md#4-에러-코드-목록).

### 3.4 웹훅 중복

| 항목 | 내용 |
| --- | --- |
| 키 | `tossPaymentKey` → `PAYMENT.payment_key` Unique |
| 중복 수신 | `DUPLICATE_PAYMENT` 또는 내부 `DUPLICATE_SKIPPED` audit |
| 원본 보관 | 90일 — [보관 정책](../erd/data-retention-and-audit-policy.md#22-결제웹훅-장성재) |

### 3.5 DB 커넥션 고갈

| 조치 | 목적 |
| --- | --- |
| `POST /queue/join` 제한 강화 | 유입 차단 |
| `POST /orders` 일시 429 | write 보호 |
| Hikari `maximum-pool-size` · slow query 알람 | [observability](./observability-metrics.md) |

### 3.6 캐시 miss 폭증

| 조치 | 적용 |
| --- | --- |
| SingleFlight (per key) | 상품 상세·피드 목록 |
| TTL jitter | 동시 만료 방지 |
| Circuit breaker (선택) | DB 에러율 상승 시 |

---

## 4. 수동 복구 (MVP)

자동 Saga가 실패하거나 운영 개입이 필요할 때. **모든 수동 조작은 `audit_logs` 필수** ([§3 Admin audit](../erd/data-retention-and-audit-policy.md#32-admin-api-표지민--지영재)).

| 작업 | API / 절차 | 전제 | audit `reason` 예시 |
| --- | --- | --- | --- |
| 재고 복구 + 주문 취소 | `Admin`: `orderId` 기준 restore + `CANCELLED` | `RESERVED`/`FAILED` 정체 | `MANUAL_STOCK_RESTORE` |
| PAID 정체 재처리 | `inventory.confirm` 재실행 Job 트리거 | `PAID` stuck | `MANUAL_COMPLETE_RETRY` |
| 웹훅 재처리 | PG 대시보드 + `tossPaymentKey` 멱등 확인 후 수동 reconcile | 리컨실 90일 이내 | `MANUAL_WEBHOOK_REPLAY` |
| Outbox 재발행 | `outbox_events` pending 일괄 publish | worker 장애 복구 후 | `MANUAL_OUTBOX_REPUBLISH` |

> 자동 Saga([§5 상태 머신](../state/invariants-and-state-machines.md#5-saga-보상-compensation)) 안정화 후, 동일 케이스는 Admin 수동 대신 **재처리 Job**으로 이전한다.

### 금지 (MVP)

| 금지 | 이유 |
| --- | --- |
| `COMPLETED` → 다른 상태 | Terminal — [O-6](../state/invariants-and-state-machines.md#24-종료되돌릴-수-없는-상태) |
| 클라이언트가 결제 상태 직접 변경 | [API confirm 비노출](../api/mvp-api-spec.md#confirm--fail-api-비노출) |
| audit 없는 Admin 재고 조정 | 조작 추적 불가 |

---

## 5. 장애 ↔ API 계약 매핑

| 장애 | HTTP / `error.code` | `retryable` |
| --- | --- | --- |
| Redis/진입 불가 | 503 / `INTERNAL_ERROR` | true |
| RateLimit | 429 / `RATE_LIMITED` | true |
| 재고 경쟁 패배 | 409 / `RESERVE_FAILED` | true |
| 전체 품절 | 409 / `OUT_OF_STOCK` | false |
| 대기열 토큰 만료 | 403 / `INVALID_QUEUE_TICKET` | false |
| Saga 진행 중 결제 실패 | 402 / `PAYMENT_FAILED` | false |

상세 envelope: [api-contract.md](../api/api-contract.md).

---

## 6. 복구 검증 체크리스트

장애 복구 후 배포·트래픽 복구 전:

- [ ] `outbox_pending_count` ≈ 0 또는 감소 추세
- [ ] `ORDER` in `FAILED` > 5분 건 0건
- [ ] `reserved_qty` vs 주문 합계 리컨실
- [ ] 5xx < 0.1%, Write P95 < 300ms ([SLO](./observability-metrics.md#2-slo-목표-mvp))
- [ ] 드롭스 smoke: queue join → order → (test PG) → COMPLETED

---

## 7. 관련 문서

| 문서 | 설명 |
| --- | --- |
| [incident-response.md](./incident-response.md) | P0/P1/P2 대응 |
| [observability-metrics.md](./observability-metrics.md) | 알람 메트릭 |
| [invariants-and-state-machines.md](../state/invariants-and-state-machines.md) | 타임아웃·Saga |
