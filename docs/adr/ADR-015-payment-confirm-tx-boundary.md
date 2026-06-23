# ADR-015: PaymentConfirmService TX 경계 분리 — TxHelper 패턴 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-23 |
| **선행 ADR** | [ADR-006 결제 confirm 멱등성](./ADR-006-payment-confirm-idempotency.md) · [ADR-011 결제 Saga](./ADR-011-payment-saga-choreography.md) |
| **관련** | [k6 튜닝 결과](../operations/k6-tuned-results.md) · [결제 플로우](../sequence/payment-flow-reason.md) |
| **담당** | 장성재 (`payment`) |

---

## Title

`PaymentConfirmService.confirm()`의 `@Transactional`을 제거하고, DB 접근 로직을 **`PaymentConfirmTxHelper`로 분리**하여 PG HTTP 호출이 트랜잭션 밖에서 실행되도록 한다. WebClient 비동기 전환은 채택하지 않는다.

---

## Context

### 발단 — k6 s03 부하 테스트 결과

`POST /api/v1/payments/toss/confirm` (시나리오 s03, VU=50) 결과:

| 지표 | 측정값 | SLO |
| --- | --- | --- |
| P95 응답시간 | 1,940ms | 2,000ms |
| Max 응답시간 | 2,330ms | — |
| 여유 | **60ms** | — |

P95가 SLO에 60ms 남짓 붙어 있다. 현재 테스트에는 Wiremock이 Toss PG를 대신하므로 네트워크 지연이 사실상 없다. 실 PG 환경에서 Toss의 일반적인 응답 지연(100~300ms)이 추가되면 P95가 SLO를 초과할 가능성이 높다.

### 근본 원인 분석

기존 구현에서 `PaymentConfirmService.confirm()`은 단일 `@Transactional`로 묶여 있었다.

```
[기존 단일 TX]
BEGIN
  findByTossPaymentKey()     ← DB I/O
  findByOrderId()            ← DB I/O
  tossPaymentPort.confirm()  ← PG HTTP 호출 (Wiremock: ~0ms, 실 PG: 100~300ms)
  payment.confirm()
  paymentRepository.save()   ← DB I/O
COMMIT
```

`tossPaymentPort.confirm()`이 응답을 기다리는 동안 DB 커넥션이 TX 안에 묶인 채 유지된다. HikariCP 기본 풀 크기(10)에서 VU=50이 동시에 PG 응답을 기다리면 커넥션 부족으로 대기 큐가 쌓이고, 대기 스레드 자체가 응답 지연을 증폭시킨다.

```
[병목 구조]
VU 50개 → confirm() 진입 → 각자 DB 커넥션 점유 → PG HTTP 대기 중
                                                         ↑
                         HikariCP 풀(10) 고갈 → 후속 요청 대기 큐
```

HikariCP 풀 사이즈를 늘리는 방안도 있으나, 이는 증상 완화일 뿐이다. DB 커넥션은 DB 서버 쪽 연결 자원과도 연동되므로 단순 확장이 근본 해결책이 되지 않는다.

---

## 방안 비교

### A안 — 현행 유지 (단일 `@Transactional`)

PG 호출이 TX 안에 묶인 현재 구조를 그대로 유지한다.

| 관점 | 평가 |
| --- | --- |
| 구현 변경 | 없음 |
| DB 커넥션 점유 | PG 응답 대기 시간만큼 커넥션 보유 |
| 처리량 | VU 증가 시 커넥션 풀 조기 고갈 |
| 실 PG 전환 시 위험 | P95 SLO 초과 가능 (60ms 여유로는 부족) |
| **기각 이유** | Wiremock 기준으로도 P95가 SLO에 60ms 남짓이다. 실 Toss PG의 응답 지연(100~300ms)을 고려하면 SLO 위반이 거의 확실하다. 구조적 결함을 그대로 두는 것은 수용할 수 없다. |

### B안 — TxHelper 패턴 (TX 경계 분리) ← **채택**

DB 접근 로직을 `PaymentConfirmTxHelper`(별도 Spring Bean)로 추출한다. `PaymentConfirmService.confirm()`은 `@Transactional` 없이 orchestrator 역할만 수행한다.

```
[분리 후 TX 구조]

TX-1 (precheck)
  BEGIN
    findByTossPaymentKey()
    findByOrderId() or save()
    상태 검증, 금액 검증
  COMMIT                          ← 커넥션 반납

  ↑ 이 사이 구간 — DB 커넥션 점유 없음
  tossPaymentPort.confirm()       ← PG HTTP 호출 (TX 밖)
  ↓ @Version 낙관적 락이 타임 윈도 보호

TX-2 (applySuccess 또는 applyFailure)
  BEGIN
    payment.confirm() / payment.fail()
    paymentRepository.save()
    eventPublisher.publishEvent()
  COMMIT
```

PG HTTP 대기 구간에 DB 커넥션이 없으므로, VU 50개가 동시에 PG 응답을 기다려도 커넥션 풀을 점유하지 않는다.

| 관점 | 평가 |
| --- | --- |
| DB 커넥션 점유 | PG 응답 대기 중 커넥션 없음 |
| 처리량 | PG 지연 증가 시에도 커넥션 풀 여유 유지 |
| 멱등 정합성 | `@Version` 낙관적 락이 precheck-PG 구간의 동시 요청 충돌 방어 |
| Spring Bean 구조 | TxHelper를 별도 Bean으로 등록해야 Spring AOP 프록시가 `@Transactional`을 인터셉트 |
| 구현 복잡도 | 낮음 — 클래스 2개 추가, 기존 Bean 등록 수정 |
| 단점 | precheck TX 커밋 후 ~ applySuccess TX 시작 전 타임 윈도 존재 (아래 별도 설명) |

**타임 윈도 상세:**

`precheck TX`가 커밋된 직후 PG를 호출하기 전, 다른 요청이 동일 orderId로 `precheck()`를 통과할 수 있다. 두 요청이 모두 PG 승인을 받으면 `applySuccess()`에서 `@Version` 충돌이 발생한다.

```
요청 A: precheck ──commit── PG 호출 ──────── applySuccess (version=1→2) ✅
요청 B:     precheck ──commit── PG 호출 ── applySuccess (OptimisticLockException) ❌
```

Toss PG는 `tossPaymentKey` 기준 멱등을 자체 보장하므로 두 번째 PG 호출은 성공하더라도 `applySuccess`의 `@Version` 체크가 충돌을 감지해 거부한다. 이는 ADR-006에서 채택한 낙관적 락 전략과 동일 방어선이다.

### C안 — WebClient 비동기 전환

`tossPaymentPort.confirm()`을 `WebClient`로 비동기 호출하고, Mono/CompletableFuture 체인으로 처리한다.

| 관점 | 평가 |
| --- | --- |
| DB 커넥션 점유 | 해결 (비동기 대기) |
| 처리량 | 이론적으로 가장 높음 |
| 구현 복잡도 | 높음 — 전체 호출 스택 리액티브 전환, 트랜잭션 전파 별도 처리 |
| ADR-001 정합성 | Spring MVC 기반 프로젝트에서 리액티브 혼용은 스레드 컨텍스트·트랜잭션 전파 등 부작용 위험 |
| **기각 이유** | 현재 스택(Spring MVC, JPA)에서 WebClient 비동기 혼용은 트랜잭션 전파·에러 처리·테스트 전략 전반을 재설계해야 한다. B안으로 충분히 해결 가능한 문제에 리액티브 스택을 도입하는 것은 ADR-001의 "측정 후 필요한 경우에만 복잡도 추가" 원칙에 반한다. |

---

## Decision

**B안(TxHelper 패턴) 채택.**

PG HTTP 호출을 TX 밖으로 이동하는 것만으로 커넥션 점유 문제를 해결할 수 있다. 추가 인프라나 스택 전환 없이 클래스 분리만으로 구현되므로 위험이 낮고, 기존 `@Version` 낙관적 락 전략(ADR-006)이 타임 윈도 정합성을 그대로 커버한다.

---

## 핵심 구현 결정 3가지

### ① `PaymentConfirmTxHelper`를 별도 Spring Bean으로 등록하는 이유

Spring의 `@Transactional`은 AOP 프록시로 동작한다. `PaymentConfirmService` 내부에 `@Transactional` 메서드를 두면 `this.precheck()` 형태로 호출되어 프록시를 우회하고 트랜잭션이 시작되지 않는다. `PaymentConfirmTxHelper`를 **별도 Bean**으로 주입받아 `txHelper.precheck()`로 호출해야 프록시를 경유해 트랜잭션이 올바르게 적용된다.

```java
// PaymentConfirmService — TX 없음
public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
    PrecheckResult precheck = txHelper.precheck(command);  // ← 프록시 경유 → TX 시작
    TossConfirmResult pgResult = tossPaymentPort.confirm(...);  // TX 밖
    if (pgResult.isSuccess()) return txHelper.applySuccess(...);  // ← 새 TX 시작
    throw txHelper.applyFailure(...);                             // ← 새 TX 시작
}
```

`@Component`가 아닌 `TossPaymentConfig`의 `@Bean` 수동 등록 방식을 유지하는 이유는, 인프라 설정(Toss API URL·키)과 도메인 서비스 Bean 등록이 같은 설정 클래스에서 관리되어 의존 관계가 명시적으로 드러나기 때문이다.

### ② `applyFailure()`가 `void`가 아닌 `PaymentConfirmFailedException`을 반환 타입으로 갖는 이유

```java
@Transactional(noRollbackFor = PaymentConfirmFailedException.class)
public PaymentConfirmFailedException applyFailure(...) {
    payment.fail(Instant.now());
    paymentRepository.save(payment);
    eventPublisher.publishEvent(new PaymentFailedEvent(orderId));
    throw new PaymentConfirmFailedException(errorCode, errorMessage);
}
```

메서드 내부에서 반드시 throw하므로 실제로 반환값을 사용하지는 않는다. 그러나 반환 타입을 `PaymentConfirmFailedException`으로 선언하면 호출부에서 `throw txHelper.applyFailure(...)` 형태로 작성할 수 있다. 이렇게 하면 컴파일러가 이후 코드가 도달 불가임을 정적으로 인식하여, 호출 후에 `return`을 추가하지 않아도 컴파일 오류가 발생하지 않는다.

반환 타입이 `void`이면 컴파일러 입장에서 메서드가 정상 반환할 수 있으므로, 호출부에서 `txHelper.applyFailure(...)` 이후 반환값이 없어 컴파일 오류가 발생한다.

### ③ precheck TX — `payment_key` 조회 우선, `order_id` 조회 후속

```java
@Transactional
public PrecheckResult precheck(PaymentConfirmCommand command) {
    return paymentRepository.findByTossPaymentKey(command.getTossPaymentKey())
            .map(existing -> PrecheckResult.done(PaymentConfirmResult.from(existing)))
            .orElseGet(() -> precheckByOrderId(command));
}
```

`tossPaymentKey` 기준 선조회를 우선하는 이유: 동일 `tossPaymentKey`로 재요청이 들어오면 PG를 다시 호출하지 않고 저장된 결과를 즉시 반환한다. Toss PG가 `tossPaymentKey` 기준 멱등을 보장하더라도 불필요한 HTTP 호출을 줄이는 것이 응답 지연과 PG API 할당량 양면에서 유리하다. (ADR-006 P-1 원칙)

`orderId` 기준 조회에서 레코드가 없으면 `lazy-create`한다. `POST /orders` 시점에 payment 레코드를 생성하지 않는 구조이므로, confirm 시점에 없으면 새로 만든다. 이렇게 생성된 레코드는 precheck TX에서 커밋되므로, PG 실패 후 재시도 시 `findByOrderId`로 재활용된다.

---

## Consequences

### 긍정

- PG HTTP 응답 대기 중 DB 커넥션 미점유 → VU 증가 시에도 커넥션 풀 여유 확보
- 실 Toss PG 전환(100~300ms 추가 지연) 시에도 P95 SLO 여유 확보 가능
- 기존 `@Version` 낙관적 락 전략(ADR-006)이 타임 윈도 정합성을 그대로 커버
- 추가 인프라 없음, Spring MVC·JPA 스택 유지

### 부정 · 수용

- precheck TX 커밋 ~ applySuccess TX 시작 사이 타임 윈도에서 `@Version` 충돌 발생 가능 → 클라이언트 재시도 필요 (`retryable: true` 응답 계약 유지, ADR-006 불변)
- TX 흐름이 두 클래스로 분산되어 직관성 감소 → 코드 주석과 이 ADR로 보완

### 불변

| 규칙 | 내용 |
| --- | --- |
| PG 호출 위치 | `PaymentConfirmService.confirm()` 안, TX 밖 |
| TxHelper 호출 방식 | 반드시 Bean 주입 후 `txHelper.메서드()` — `this.` 직접 호출 금지 |
| `applyFailure` noRollbackFor | `PaymentConfirmFailedException` — 제거 시 FAILED 상태 커밋 불가, 이중 결제 위험 |
| `@Version` 충돌 응답 | `retryable: true` 포함 (ADR-006 불변 계승) |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 결제 confirm 멱등성 | [ADR-006](./ADR-006-payment-confirm-idempotency.md) |
| 결제 Saga | [ADR-011](./ADR-011-payment-saga-choreography.md) |
| k6 부하 테스트 결과 | [k6-tuned-results.md](../operations/k6-tuned-results.md) |
| 결제 상태 머신 | [invariants-and-state-machines.md §3](../state/invariants-and-state-machines.md) |
| 결제 시퀀스 | [payment-flow-reason.md](../sequence/payment-flow-reason.md) |