# 결제·주문 시퀀스 설계 문서

> **다이어그램:** [`payment-order-flow.png`](./payment-order-flow.png)  
> 대기열 진입부터 결제 웹훅·Saga 보상까지의 흐름과 설계 근거를 정리한다.

---

## 1. 대기열 토큰 검증을 OrderService에서 수행

### 설계 결정

WaitQueueService가 발행한 Access Ticket을 StoreAPI가 아닌 **OrderService에서 검증**한다.

### 근거

StoreAPI 레벨에서만 검증하면 OrderService API를 직접 호출하는 **우회 공격**이 가능하다. 주문 생성의 진입점인 OrderService에서 토큰을 검증해야 대기열을 거치지 않은 요청을 원천 차단할 수 있다.

```
Fan → WaitQueueService        : 대기열 등록 (F08-01)
WaitQueueService → Fan        : 순번 안내 (SSE)
WaitQueueService → Fan        : 진입 토큰 발행 (Access Ticket)
Fan → StoreAPI                : 주문 요청 (대기열 토큰 포함)
StoreAPI → OrderService       : 대기열 토큰 검증 및 주문 생성 (status=PENDING)
```

---

## 2. 재고 부족 시 PENDING → CANCELLED, 주문 실패 알림 발행

### 설계 결정

재고 예약(lock) 실패 시 RESERVED를 거치지 않고 **PENDING → CANCELLED**로 직행하고, **주문 실패 알림(재고 부족)**을 발행한다.

### 근거

`RESERVED`는 재고 선점이 **성공했을 때만** 의미 있는 상태다. 재고가 없으면 선점 자체가 불가능하므로 `RESERVED` 상태를 부여하는 것이 논리적으로 맞지 않는다.

> **주의:** "품절 알림"이 아닌 **"주문 실패 알림(재고 부족)"** 을 발행하는 이유  
> 재고 예약 실패는 "내 예약만 실패"한 상황일 수 있다. 동시 경쟁에서 밀린 것이지 상품 전체가 품절된 것이 아니다.  
> **품절 알림**은 `stock_quantity`가 실제로 0이 됐을 때만 발행한다.  
> 두 개념을 혼용하면 재고가 남아 있는데 팬이 품절로 오인하는 문제가 생긴다.

```
[재고 부족] 분기
InventoryService → OrderService       : 예약 실패
OrderService                          : 상태 전이 (PENDING→CANCELLED)
OrderService → NotificationService    : 주문 실패 알림 발행 (재고 부족)
```

---

## 3. 결제 성공 시 PAID를 독립 상태로 분리

### 설계 결정

결제 성공 후 `RESERVED → PAID → COMPLETED`로 **두 단계**를 거친다.

### 근거

| 단계 | 의미 | 처리 내용 |
| --- | --- | --- |
| `RESERVED → PAID` | 결제 승인 확정 시점 기록 | PG 웹훅 수신 직후 저장 |
| `PAID → COMPLETED` | 후처리 완료 시점 기록 | 재고 확정 차감 + 알림 발행 완료 후 저장 |

`PAID`와 `COMPLETED`를 합치면 재고 차감 또는 알림 발행이 실패했을 때 **결제는 됐는데 주문이 어떤 상태인지** 추적이 불가능해진다. `PAID`에서 멈춘 주문을 별도로 감지하고 재처리할 수 있어야 한다.

```
[결제 성공] 분기
OrderService                    : 상태 전이 (RESERVED→PAID) ← DB 저장
InventoryService                : 재고 확정 (reserved_quantity 감소, stock_quantity 차감)
OrderService                    : 상태 전이 (PAID→COMPLETED) ← DB 저장
NotificationService             : 결제완료 알림 발행
```

---

## 4. 결제 실패 시 FAILED를 DB에 실제 저장

### 설계 결정

결제 실패 시 `FAILED`를 내부 중간값으로만 쓰지 않고 **DB에 실제 저장**한 뒤 `CANCELLED`로 전이한다.

### 근거

**첫째**, ERD의 `PAYMENT.failed_at` 컬럼이 이 설계를 전제한다. `FAILED`를 저장하지 않으면 `failed_at`에 기록할 시점이 없다.

**둘째**, Saga 보상 트랜잭션의 트리거 기준이 `FAILED` 상태다. 보상 실행 중 장애가 발생했을 때 `FAILED`로 저장된 레코드가 있어야 재처리 대상을 특정할 수 있다.

**셋째**, SLO 문서에서 요구하는 *"수동 리컨실리에이션 로그"* 를 남기려면 실패 시점 상태가 DB에 남아 있어야 한다.

> `FAILED`는 **Transient** 상태다. 최종 상태가 아니며 Saga 보상이 완료되는 즉시 `CANCELLED`로 전이한다.  
> `FAILED`에서 멈춘 주문이 있다면 보상 트랜잭션이 실패한 것이므로 **별도 모니터링 대상**이다.

```
[결제 실패] 분기
OrderService                    : 상태 전이 (RESERVED→FAILED) — DB 저장, failed_at 기록
InventoryService                : 재고 복구 (Saga 보상) — reserved_quantity 복구
OrderService                    : 상태 전이 (FAILED→CANCELLED) — DB 저장
NotificationService             : 결제 실패 알림 발행
```

---

## 5. PaymentService를 Webhook Receiver로 명명

### 설계 결정

`PaymentService` 대신 **`PaymentService (Webhook Receiver)`** 로 명시한다.

### 근거

토스페이먼츠 결제는 동기 응답이 아닌 **비동기 웹훅** 방식으로 결과를 수신한다. 명칭에 역할을 명시하지 않으면 OrderService가 PaymentService를 동기 호출하는 구조로 오해할 수 있다. 웹훅 수신 주체를 명확히 해야 **멱등키 처리**와 **재전송 대응** 로직의 구현 위치가 자명해진다.

```
ERD: PAYMENT.payment_key (Unique Index)
        ↕ 연결
시퀀스: PaymentService (Webhook Receiver) — idempotent key 처리
```

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 시퀀스 다이어그램 | [`payment-order-flow.png`](./payment-order-flow.png) |
| ERD 설계 | [`../erd/erd-design.md`](../erd/erd-design.md) |
| 불변조건 · 상태 머신 | [`../state/invariants-and-state-machines.md`](../state/invariants-and-state-machines.md) |
