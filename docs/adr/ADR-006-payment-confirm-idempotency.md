# ADR-006: 결제 confirm 멱등성 구현 방식 — 낙관적 락 + DB UNIQUE

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-29 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |
| **관련** | [ERD §3 PAYMENT](../erd/erd-design.md#3-payment) · [결제 플로우](../sequence/payment-flow-reason.md) |
| **담당** | 장성재 (`payment`) |

---

## Title

`POST /api/v1/payments/toss/confirm`의 중복 요청 방어는 **`payment_key` DB UNIQUE 제약 + `@Version` 낙관적 락** 조합으로 구현한다. Redis 분산락과 SELECT FOR UPDATE는 채택하지 않는다.

---

## Context

드롭스 오픈런 환경에서 클라이언트가 네트워크 오류 등으로 동일 `tossPaymentKey`로 confirm을 재요청할 수 있다. Toss PG는 `tossPaymentKey` 기준으로 자체 멱등을 보장하지만, 서버 측에서도 중복 처리를 방어해야 한다.

멱등성 방어 레이어를 어디에 둘지 세 가지 방안을 검토했다.

---

## 방안 비교

### A안 — `payment_key` DB UNIQUE + `@Version` 낙관적 락 ← **채택**

```
1. findByTossPaymentKey 선조회 → 기존 결과 즉시 반환 (PG 재호출 없음)
2. UNIQUE 제약이 최후 방어선 — 동시 중복 INSERT 시 DB가 거부
3. @Version 낙관적 락 — 동일 Payment 행 동시 수정 시 충돌 감지
```

| 관점 | 평가 |
| --- | --- |
| 인프라 의존 | 없음 (DB만 사용) |
| 처리량 | 락 대기 없음 — 고트래픽 오픈런에 유리 |
| 정합성 | PG 자체 멱등 + UNIQUE로 충분 |
| 복잡도 | 낮음 |
| 단점 | 극히 드문 동시 confirm 2건의 경우 `DataIntegrityViolationException` 발생 → advice에서 멱등 응답으로 변환 필요 (별도 MEDIUM 잔여 이슈) |

### B안 — Redis Redisson 분산락 (`LOCK:payment:{tossPaymentKey}`)

| 관점 | 평가 |
| --- | --- |
| 인프라 의존 | Redis 가용성 필수 — Redis 다운 시 결제 불가 |
| 처리량 | 명시적 락 대기 — 락 해제 전까지 후속 요청 블로킹 |
| 정합성 | 명시적 직렬화 |
| 복잡도 | 높음 (락 TTL·해제 예외 처리 필요) |
| **기각 이유** | ADR-001에서 Redis는 대기열·캐시 용도로만 한정. 결제 confirm에 Redis 가용성 의존을 추가하면 Redis 장애가 결제 중단으로 직결됨. PG 자체 멱등으로 충분한 상황에서 과잉 설계. |

### C안 — SELECT FOR UPDATE 비관적 락

| 관점 | 평가 |
| --- | --- |
| 인프라 의존 | 없음 |
| 처리량 | DB 연결 선점 → 드롭스 오픈런 시 커넥션 풀 고갈 위험 |
| 정합성 | 강력한 직렬화 |
| 복잡도 | 중간 (JPA `PESSIMISTIC_WRITE` 적용 필요) |
| **기각 이유** | 드롭스 구간 동시 결제 요청이 집중될 때 락 대기로 인한 응답 지연·타임아웃이 오히려 더 큰 문제를 유발. ADR-001 §성능 원칙(측정 후 튜닝)에 따라 낙관적 접근을 먼저 적용. |

---

## Decision

**A안 채택.**

Toss PG가 `tossPaymentKey` 기준 멱등을 자체 보장하므로 application 레이어에서 강한 락은 불필요하다. DB UNIQUE 제약이 최후 방어선으로 충분하며, `findByTossPaymentKey` 선조회로 대부분의 중복 요청을 PG 재호출 없이 차단할 수 있다. Redis 미의존(ADR-001 준수), 낙관적 락으로 고트래픽 처리량을 유지한다.

**중복 INSERT 발생 시 동작:**
- `payment_key` UNIQUE 위반 → `DataIntegrityViolationException`
- `PaymentControllerAdvice`에서 멱등 200 또는 409로 변환 (미구현 — MEDIUM 잔여 이슈)

---

## Consequences

### 긍정

- Redis 의존 없이 결제 멱등 보장
- 드롭스 오픈런 시 락 대기 없는 고처리량 유지
- 구현 단순, 테스트 용이 (P-1 단위 테스트로 멱등 검증)

### 부정 · 수용

- 극히 드문 동시 confirm 2건의 충돌 시 500 응답 가능 → advice 핸들러 추가로 해소 예정
- `@Version` 낙관적 충돌 시 클라이언트 재시도 필요 (`retryable: true` 응답 계약 유지)

### 불변

| 규칙 | 내용 |
| --- | --- |
| `payment_key` 식별자 | `tossPaymentKey`를 DB `payment_key` 컬럼에 저장. `orderPaymentKey`(ORDER 소유)와 별개 |
| 상태 변경 API | 클라이언트가 `PAID`/`COMPLETED` 직접 변경 API 없음 — 웹훅 내부 전용 |
| 재시도 응답 계약 | `RATE_LIMITED` · 낙관적 락 충돌 응답에 `retryable: true` 포함 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| ERD PAYMENT | [erd-design.md §3](../erd/erd-design.md#3-payment) |
| 결제 상태 머신 | [invariants-and-state-machines.md §3](../state/invariants-and-state-machines.md) |
| 결제 시퀀스 | [payment-flow-reason.md](../sequence/payment-flow-reason.md) |
| API 계약 | [api-contract.md](../api/api-contract.md) |