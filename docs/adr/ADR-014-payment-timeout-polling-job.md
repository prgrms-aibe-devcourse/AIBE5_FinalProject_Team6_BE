# ADR-014: 결제 타임아웃 처리 전략 — 폴링 Job 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-22 |
| **선행 ADR** | [ADR-011 Choreography Saga](./ADR-011-payment-saga-choreography.md) |
| **관련** | [invariants §4.1 타임아웃](../state/invariants-and-state-machines.md) · [결제 플로우](../sequence/payment-flow-reason.md) · [failure-policy.md](../operations/failure-policy.md) |
| **담당** | 장성재 (`payment`) |

---

## Title

결제 창을 이탈하거나 네트워크가 단절되어 PENDING 상태가 방치된 결제는 **`@Scheduled` 폴링 Job(60s 주기, PT15M 임계값)** 으로 타임아웃 처리한다. 이벤트 기반, 외부 스케줄러는 채택하지 않는다.

---

## Context

정상 결제 실패 경로(ADR-011 케이스 A)는 `PaymentConfirmService`가 PG 실패 응답을 받아 `PaymentFailedEvent`를 발행하고, `@Async` 리스너가 주문·재고를 즉시 보상한다. 그러나 **confirm API 자체가 호출되지 않는 케이스**가 존재한다.

```
[케이스 B — 사용자 이탈·네트워크 단절]
POST /orders → RESERVED (재고 예약됨)
사용자가 결제 창을 이탈하거나 앱을 종료
→ POST /payments/toss/confirm 호출 없음
→ 이벤트 발행 주체 없음
→ PENDING 상태 무한 방치 위험
```

PENDING 상태가 방치되면 재고가 예약된 채 묶여 다른 팬이 구매할 수 없다. 반드시 일정 시간 후 주문을 취소하고 재고를 복원해야 한다.

이 케이스를 어떻게 처리할지 세 가지 방안을 검토했다.

---

## 방안 비교

### A안 — 이벤트 기반 즉시 처리

사용자 이탈·타임아웃 이벤트를 감지해 즉시 보상 이벤트를 발행한다.

| 관점 | 평가 |
| --- | --- |
| 수렴 속도 | 이탈 감지 즉시 — 빠름 |
| 구현 가능성 | **불가** |
| 이유 | 사용자가 결제 창을 이탈하거나 앱을 종료하면 서버에 아무 요청도 오지 않는다. **이벤트를 발행할 주체가 없다.** WebSocket/SSE로 disconnect를 감지하더라도 결제 모듈은 SSE를 연결하지 않는다. |
| **기각 이유** | confirm 미호출 시나리오에서 이벤트 발행 주체가 구조적으로 존재하지 않는다. 이 방안은 케이스 B를 커버할 수 없다. |

### B안 — 외부 스케줄러 (AWS EventBridge / Quartz Cluster)

AWS EventBridge Scheduler나 Quartz 클러스터로 외부에서 주기적 트리거를 발행한다.

| 관점 | 평가 |
| --- | --- |
| 신뢰성 | 높음 — JVM 재시작과 무관 |
| 정밀도 | 높음 — cron 표현식으로 초 단위 제어 |
| 인프라 | EventBridge: AWS 추가 서비스 / Quartz: 별도 클러스터 DB 테이블 필요 |
| 복잡도 | 높음 — 외부 트리거 → HTTP 또는 SQS 연동 필요 |
| **기각 이유** | ADR-001에서 외부 메시지 브로커·큐(Kafka, SQS)는 MVP 스코프 밖. 타임아웃 처리는 JVM 내부 `@Scheduled`로 충분하며, JVM 재시작 시 PENDING 재처리는 다음 폴링 주기(최대 60s)에서 자연스럽게 처리된다. 인프라 복잡도 대비 이점이 없다. |

### C안 — `@Scheduled` 폴링 Job ← **채택**

Spring `@Scheduled`로 60초마다 DB를 폴링해 PT15M 이상 경과한 PENDING 결제를 일괄 처리한다.

```java
// PaymentTimeoutJob.java
@Scheduled(fixedDelay = 60_000)
public void run() {
    paymentTimeoutService.cancelTimedOutPayments(paymentTimeout);  // 기본값 PT15M
}

// PaymentTimeoutService.java
public void cancelTimedOutPayments(Duration timeout) {
    Instant threshold = Instant.now().minus(timeout);
    List<Payment> timedOut = paymentRepository.findPendingOlderThan(threshold);
    for (Payment payment : timedOut) {
        itemProcessor.process(payment);  // PaymentFailedEvent 발행 → Saga 보상
    }
}
```

| 관점 | 평가 |
| --- | --- |
| 구현 가능성 | 가능 — Spring 내장 `@Scheduled` |
| 수렴 속도 | 최악 PT15M + 60s ≈ 16분 |
| 케이스 B 커버 | ✅ — confirm 미호출 케이스 포함 모든 PENDING 처리 |
| 인프라 | 없음 |
| 단점 | 폴링 주기(60s)마다 DB 조회 발생 |

---

## Decision

**C안(`@Scheduled` 폴링 Job) 채택.**

케이스 B(사용자 이탈)에서 이벤트 발행 주체가 없으므로 이벤트 기반은 구조적으로 불가능하다. 외부 스케줄러는 MVP 스코프 밖 인프라를 요구한다. `@Scheduled` 폴링이 confirm 미호출 케이스를 포함한 **모든 PENDING 방치 케이스를 커버하는 유일한 안전망**이다.

---

## 핵심 설계 결정 2가지

### ① PT15M 임계값 — 왜 15분인가

임계값을 너무 짧게 잡으면 **정상 결제 진행 중인 사용자의 주문이 취소**된다.

| 시나리오 | 소요 시간 |
| --- | --- |
| 일반 카드 결제 | 수초 |
| 간편결제 앱 전환·인증 | 최대 수분 |
| 일부 카드사 승인 지연 | 최대 10분 |
| 해외 카드 승인 | 최대 10분+ |

카드사 승인 지연이 최대 10분대에 분포하므로 임계값이 10분 이하이면 정상 승인 중인 결제가 취소될 수 있다. **PT15M은 카드사 최대 지연(~10분)에 여유(5분)를 더한 값**이다. 단, 사용자 관점에서 재고가 15분간 묶이므로 인벤토리 회전율에 영향을 준다 — 트레이드오프로 수용한다.

```java
// PaymentTimeoutJob.java:24
@Value("${fandrops.order.payment-timeout:PT15M}") Duration paymentTimeout
```

`fandrops.order.payment-timeout` 환경변수로 운영 중 조정 가능하다.

### ② fixedDelay = 60s — 왜 60초 폴링 주기인가

폴링 주기가 짧을수록 수렴 시간이 빠르지만 DB 조회 부하가 증가한다.

```
수렴 시간 = 임계값(PT15M) + 폴링 주기(최대 60s) = 최대 16분
```

임계값이 PT15M이므로 초 단위 정밀도는 의미 없다. 60s 주기는 `findPendingOlderThan(threshold)` DB 조회를 분당 1회로 제한하면서 최대 수렴 시간(16분)을 수용 가능한 범위로 유지한다.

드롭스 오픈런 집중 시간(수분) 동안 폴링 부하가 겹치더라도 `findPendingOlderThan`은 생성 시각 인덱스 기반 범위 조회이므로 결제 전체 건수에 비례하지 않는다.

---

## 케이스 A vs 케이스 B 비교

두 실패 경로는 처리 속도가 다르며 이는 의도된 설계다.

| 구분 | 트리거 | 처리 방식 | 수렴 시간 |
| --- | --- | --- | --- |
| **케이스 A** — confirm 실패 응답 | PG가 실패 응답 반환 | `PaymentFailedEvent` → `@Async` 리스너 즉시 처리 | 수초 이내 |
| **케이스 B** — 사용자 이탈·PENDING 방치 | 없음 (confirm 미호출) | `@Scheduled` 폴링 Job | 최대 PT15M + 60s |

케이스 B의 16분 수렴은 SLO 위반이 아니다. 기획 SLO "결제 실패 시 60초 내 수렴"은 confirm API가 호출된 케이스 A를 가리킨다. 케이스 B의 PT15M은 `invariants-and-state-machines.md §4.1`에 팀 합의 기준으로 명시된 의도된 설계값이다.

---

## Consequences

### 긍정

- confirm 미호출 케이스 포함 모든 PENDING 방치를 커버하는 유일한 안전망
- Spring `@Scheduled` 내장 — 추가 인프라 없음
- `fandrops.order.payment-timeout` 환경변수로 운영 중 임계값 조정 가능
- 개별 payment 처리 실패 시 다음 폴링에서 재처리 (try-catch로 단건 실패가 전체 배치를 중단하지 않음)

### 부정 · 수용

- 최대 수렴 시간 16분 — 재고가 최대 16분 묶임 → 인벤토리 회전율 트레이드오프로 수용
- 60s마다 DB 폴링 발생 → `findPendingOlderThan` 인덱스 조회로 부하 최소화
- JVM 재시작 시 폴링 중단 → 재시작 후 첫 폴링(최대 60s)에서 누락분 일괄 처리

### 불변

| 규칙 | 내용 |
| --- | --- |
| 임계값 기본값 | PT15M — 카드사 최대 승인 지연 커버 |
| 처리 방식 | `PaymentFailedEvent` 발행 → ADR-011 Saga 보상 경로 재사용 |
| 단건 실패 격리 | 개별 payment 처리 실패가 전체 배치를 중단하면 안 됨 |
| 수렴 SLO | 케이스 B는 "60초 내 수렴" SLO 대상 아님 (invariants §4.1) |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 타임아웃 불변식 | [invariants-and-state-machines.md §4.1](../state/invariants-and-state-machines.md) |
| Saga 보상 트랜잭션 | [ADR-011](./ADR-011-payment-saga-choreography.md) |
| 결제 시퀀스 | [payment-flow-reason.md](../sequence/payment-flow-reason.md) |
| 장애 정책 | [failure-policy.md](../operations/failure-policy.md) |