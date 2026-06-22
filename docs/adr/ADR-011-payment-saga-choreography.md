# ADR-011: 결제 후 주문·재고 처리 전략 — Choreography Saga 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-22 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) · [ADR-006 결제 confirm 멱등성](./ADR-006-payment-confirm-idempotency.md) |
| **관련** | [invariants §3 PAYMENT · §5 Saga](../state/invariants-and-state-machines.md) · [결제 플로우](../sequence/payment-flow-reason.md) |
| **담당** | 장성재 (`payment`) · 형성빈 (`order`, `inventory`) |

---

## Title

결제 성공·실패 후 주문 상태 전이와 재고 처리는 **Choreography Saga + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`** 조합으로 구현한다. 2PC, Orchestration Saga, 직접 호출은 채택하지 않는다.

---

## Context

`POST /api/v1/payments/toss/confirm`이 완료되면 payment 모듈 외부에서 두 가지 작업이 연쇄적으로 수행되어야 한다.

**성공 경로**
```
payment: PENDING → SUCCESS
order:   RESERVED → PAID → COMPLETED
inventory: 예약 수량 → 확정 수량
```

**실패 경로**
```
payment: PENDING → FAILED
order:   RESERVED → FAILED → CANCELLED
inventory: 예약 수량 → 복원
```

이 처리를 어떻게 구성할지 네 가지 방안을 검토했다. 핵심 제약은 다음과 같다.

- ADR-001: `payment-api` → `order`, `inventory` 모듈 **직접 의존 금지** (포트/이벤트만)
- 결제 confirm API 응답 속도 유지 — 주문·재고 처리가 결제 응답을 블로킹하면 안 됨
- 실패 시에도 `payment.fail()` 상태가 DB에 반드시 저장되어야 함 (이중 결제 방지)

---

## 방안 비교

### A안 — 직접 호출 (Direct Call)

`PaymentConfirmService`에서 `OrderService`·`InventoryService`를 직접 호출한다.

```java
// 채택하지 않은 예시
paymentConfirmService.confirm(command);
orderService.markAsPaid(orderId);          // 직접 호출
inventoryService.confirm(productId, qty);  // 직접 호출
```

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 낮음 |
| 트랜잭션 | payment TX 안에서 order·inventory까지 묶임 → 단일 TX 비대, 장시간 DB 락 |
| 모듈 의존 | payment → order, inventory **직접 참조** — ADR-001 아키텍처 위반 |
| 롤백 | PG 호출 후 order 처리 중 실패 시 payment 상태 롤백 불가능 |
| **기각 이유** | 아키텍처 하드 룰(`payment-api` → 타 도메인 인프라 직접 참조 금지) 위반. 단일 TX가 PG 네트워크 I/O를 포함해 비대해지고, 어느 한 곳 실패 시 전체 롤백 판단이 불명확해진다. |

### B안 — 2PC (Two-Phase Commit)

분산 트랜잭션 코디네이터로 payment·order·inventory 세 리소스를 원자적으로 커밋한다.

| 관점 | 평가 |
| --- | --- |
| 정합성 | ACID 보장 — 가장 강력 |
| 성능 | Prepare 단계에서 모든 참여자가 락 보유 → 처리량 저하 |
| 구현 복잡도 | 높음 — XA 트랜잭션, JTA 코디네이터 필요 |
| 인프라 | JTA 구현체(Atomikos 등) 추가 도입 필요 |
| **기각 이유** | 모놀리스(ADR-001)에서 2PC는 과잉이다. 단일 JVM 내 모듈 간 통신인데 XA 트랜잭션을 도입하면 인프라 복잡도만 증가하고 성능상 이점이 없다. 드롭스 오픈런 고처리량 환경에서 Prepare 락이 병목이 된다. |

### C안 — Orchestration Saga

중앙 Orchestrator가 payment·order·inventory에 순차적으로 명령을 보내고 실패 시 보상을 지시한다.

```
Orchestrator
  → command: ConfirmPayment
  → command: ReserveInventory
  → command: CompleteOrder
  (실패 시 역순 보상 command 발행)
```

| 관점 | 평가 |
| --- | --- |
| 흐름 가시성 | 높음 — 전체 흐름이 Orchestrator 한 곳에 명시 |
| 모듈 의존 | Orchestrator가 모든 도메인 커맨드에 의존 |
| 단일 실패 지점 | Orchestrator 장애 시 전체 Saga 중단 |
| 구현 복잡도 | 높음 — 상태 머신·커맨드 버스·보상 로직 별도 구현 |
| **기각 이유** | MVP 규모에서 Orchestrator 자체가 모든 도메인을 알아야 하는 신규 결합 지점이 된다. 모놀리스에서 모듈 간 이벤트로 충분히 해결 가능한 문제에 Orchestration을 도입하면 복잡도만 늘어난다. |

### D안 — Choreography Saga ← **채택**

payment는 결과 이벤트만 발행한다. order·inventory는 이벤트를 수신해 자신의 상태를 스스로 전이한다.

```java
// PaymentConfirmService — 이벤트 발행만
eventPublisher.publishEvent(new PaymentApprovedEvent(orderId));
eventPublisher.publishEvent(new PaymentFailedEvent(orderId));

// PaymentEventListener (order 모듈 소유) — 이벤트 수신·처리
@Async("sagaExecutor")
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handlePaymentFailed(PaymentFailedEvent event) { ... }
```

| 관점 | 평가 |
| --- | --- |
| 모듈 의존 | payment는 이벤트 발행만 — order·inventory 직접 참조 없음 |
| 성능 | `@Async`로 별도 스레드 실행 → 결제 응답 지연 없음 |
| 정합성 | `AFTER_COMMIT` 보장 — 리스너가 커밋된 payment 상태를 읽음 |
| 구현 복잡도 | 낮음 — Spring `ApplicationEventPublisher` 내장 활용 |
| 단점 | 이벤트 유실 시 보상 미실행 (MVP에서 Outbox 없이 수용, `outbox_events`는 알림 전용) |

---

## Decision

**D안(Choreography Saga) 채택.**

모놀리스 단일 JVM에서 모듈 간 의존성 없이 이벤트 발행만으로 Saga를 구성할 수 있다. `@TransactionalEventListener(AFTER_COMMIT)`으로 리스너가 항상 커밋된 상태를 읽고, `@Async("sagaExecutor")`로 결제 응답을 블로킹하지 않는다.

---

## 핵심 구현 결정 3가지

### ① `@TransactionalEventListener(AFTER_COMMIT)` — 왜 AFTER_COMMIT인가

`BEFORE_COMMIT`이나 일반 `@EventListener`를 사용하면 payment TX가 아직 커밋되지 않은 상태에서 리스너가 실행된다. 리스너(order 모듈)가 DB에서 payment 상태를 조회하면 변경 전 상태를 읽게 되어 정합성이 깨진다.

`AFTER_COMMIT`은 payment TX 커밋이 완료된 뒤 리스너를 실행하므로 항상 커밋된 상태를 기준으로 처리한다.

```
payment TX commit
       ↓
AFTER_COMMIT 이벤트 발행
       ↓
sagaExecutor 스레드에서 handlePaymentFailed() 실행
       ↓
order: RESERVED → FAILED → CANCELLED
inventory: restore
```

### ② `@Transactional(noRollbackFor = PaymentConfirmFailedException.class)` — 왜 실패 시 롤백하지 않는가

PG가 실패 응답을 반환했을 때 `PaymentConfirmService`는 `payment.fail()`로 상태를 FAILED로 설정하고 저장한다. 이 상태가 DB에 커밋되어야 `AFTER_COMMIT` 이벤트가 발행되고 order·inventory 보상이 시작된다.

롤백하면 `payment.fail()` 저장이 취소되어 payment 레코드가 PENDING 상태로 남는다. 이후 재시도 시 동일 `tossPaymentKey`로 PG를 다시 호출하게 되어 **이중 결제 위험**이 발생한다.

```java
@Transactional(noRollbackFor = PaymentConfirmFailedException.class)
public PaymentConfirmResult confirm(...) {
    ...
    payment.fail(Instant.now());
    paymentRepository.save(payment);  // ← 반드시 커밋되어야 함
    eventPublisher.publishEvent(new PaymentFailedEvent(orderId));
    throw new PaymentConfirmFailedException(...);  // 롤백 없이 커밋
}
```

### ③ 멱등 가드 — 이벤트 중복 수신 방어

`AFTER_COMMIT` 이후 `@Async` 리스너가 실패하면 Spring이 재시도하지 않는다(별도 재시도 메커니즘 없음). 반대로 이벤트가 중복 발행될 경우를 대비해 리스너에 멱등 가드를 둔다.

```java
// 이미 처리된 상태이면 즉시 스킵
if (order.getStatus() == OrderStatus.FAILED
        || order.getStatus() == OrderStatus.CANCELLED) {
    return;
}
```

---

## Consequences

### 긍정

- payment → order/inventory 직접 의존 없음 — 모듈 경계 유지 (ADR-001 준수)
- `@Async`로 결제 응답 블로킹 없음 — 고처리량 유지
- `AFTER_COMMIT` 보장 — 리스너가 항상 커밋된 상태 기준으로 동작
- Spring 내장(`ApplicationEventPublisher`) 활용 — 추가 인프라 없음

### 부정 · 수용

- 이벤트 유실(JVM 크래시, `@Async` 스레드 예외) 시 보상 미실행 → `outbox_events` + Grafana 알람(`outbox_dead_count > 0`)으로 운영 감지 (MVP 수용)
- Saga 전체 흐름이 코드에 분산되어 추적 어려움 → `payment-flow-reason.md` 시퀀스 다이어그램으로 보완

### 불변

| 규칙 | 내용 |
| --- | --- |
| 이벤트 발행 위치 | payment TX `AFTER_COMMIT` 이후만 — TX 중간 발행 금지 |
| 보상 TX 소유 | `RESERVED→FAILED`, restore, `FAILED→CANCELLED` — order 모듈 소유 |
| payment 역할 | 이벤트 **발행만** — order/inventory 포트 직접 호출 금지 |
| 실패 상태 수렴 | `FAILED`는 Transient — 반드시 `CANCELLED`로 수렴 (`FAILED` = 재고 미복원 중간 상태) |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 결제 상태 머신 · Saga | [invariants-and-state-machines.md §3·§5](../state/invariants-and-state-machines.md) |
| 결제 시퀀스 | [payment-flow-reason.md](../sequence/payment-flow-reason.md) |
| 결제 confirm 멱등성 | [ADR-006](./ADR-006-payment-confirm-idempotency.md) |
| API 계약 | [api-contract.md](../api/api-contract.md) |