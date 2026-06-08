# ADR-007: AI 에이전트 하네스 2차 튜닝 — 역방향 추적(Backward Tracing) 자가 검증 내재화

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-08 |
| **관련 문서** | [ADR-004](./ADR-004-ai-agent-harness-engineering.md) · [ADR-005](./ADR-005-ai-harness-tuning-self-reflection.md) · [SHARED](../ai/SHARED.md) |

---

## Title

ADR-004·005로 구축·1차 튜닝된 AI 하네스에 **역방향 추적(Backward Tracing)** 자가 검증 단계를 추가한다.
구현 방향(forward)이 아닌 **"이 코드가 실제 환경에서 무엇에 부딪히는가"** 를 역방향으로 추적하는 4개 체크리스트를 `SHARED.md`에 하네스로 고정한다.

---

## Context — 왜 이 결정이 필요한가

#130(결제 상세 조회 F07-02) 구현 완료 후 자체 코드리뷰를 수행한 결과, 초기 구현 시 아래 4가지 패턴이 반복적으로 누락되었음이 확인됐다.

| 누락 항목 | 실제 발생한 결함 | 심각도 |
|---|---|---|
| `payment.order_id` UNIQUE 제약 미확인 | `orElseGet(save)` lazy-create 동시 INSERT 시 중복 레코드 생성 가능 | P1 |
| `@ExceptionHandler` 역추적 미수행 | `IllegalArgumentException` → 핸들러 없음 → 500 응답 | P1 |
| `application.yml` Jackson 설정 미확인 | `Instant` 필드가 ISO-8601이 아닌 숫자로 직렬화 | P1 |
| 신규 클래스 테스트 파일 미생성 | `PaymentQueryService` 권한 우회 경로(Q-2) 등 4케이스 미검증 | P2 |

이 4가지 결함은 구조적으로 동일한 원인에서 비롯됐다.

> **AI가 코드를 작성하는 방향(forward)으로만 사고하고, 작성된 코드가 실제 환경(DB 제약, 예외 체인, 직렬화 설정, 테스트 커버리지)에서 무엇에 부딪히는가를 역방향으로 추적하지 않았다.**

ADR-005에서 "5. Retro" 단계가 도입되었으나, Retro는 작업 완료 후 사후 단계다. 결함은 그보다 앞선 Execute 직후 — 코드를 작성한 시점 — 에 잡혀야 한다. Execute와 Debug 사이에 **역방향 추적 자가 검증** 단계가 없었다는 점이 이번 튜닝의 근거다.

---

## Decision — 결정 및 수행한 작업

### 역방향 추적이란

순방향(forward) 사고: "이 코드가 올바르게 동작하는가?" → 컴파일·단위 테스트로 확인.

역방향(backward) 추적: **"이 코드가 실제 환경의 어디에 부딪히는가?"** → 코드를 완성한 뒤, 그 코드가 의존하는 외부 계층(DB 스키마, 예외 핸들러 체인, 직렬화 설정)을 거꾸로 거슬러 올라가며 불일치를 탐색한다.

예시:
- `paymentRepository.save(...)` 작성 → "DB `payment` 테이블에 `order_id` UNIQUE가 있는가?" ← V*.sql 역추적
- `throw new IllegalArgumentException(...)` 작성 → "이 예외를 잡는 `@ExceptionHandler`가 있는가?" ← `@RestControllerAdvice` 역추적
- `Instant paidAt` 응답 필드 추가 → "Jackson이 `Instant`를 어떻게 직렬화하는가?" ← `application.yml` 역추적
- 새 서비스 클래스 생성 → "대응하는 테스트 파일이 있는가?" ← `src/test/` 역추적

### SHARED.md 변경 내용

`docs/ai/SHARED.md`의 "복잡한 문제 처리" 섹션 아래에 **"Execute 직후 자가 검증"** 테이블을 신규 추가했다.

```markdown
### Execute 직후 자가 검증 (신규 코드 필수)

코드를 작성한 직후, 빌드·테스트 전에 아래 4개 질문에 답한다.
하나라도 "미확인"이면 먼저 확인하고 나서 다음 단계로 진행한다.

| # | 질문 | 확인 방법 |
|---|---|---|
| ① DB 제약   | 새 DB 접근 패턴에 필요한 UNIQUE·FK 제약이 DDL에 있는가?       | 관련 V*.sql 파일을 열어 인덱스 타입 확인 — orElseGet(save) 패턴은 반드시 확인 |
| ② 예외 핸들러 | 새 throw가 어느 @ExceptionHandler에서 잡히는가?               | @RestControllerAdvice grep — 핸들러 없으면 500 반환됨 |
| ③ 직렬화 설정 | 새 응답 필드(Instant, LocalDate 등)의 Jackson 직렬화 형식이 보장되는가? | application.yml에서 spring.jackson 설정 확인 — 기본값은 ISO-8601 아님 |
| ④ 테스트 파일 | 신규 클래스(Service, Port 구현체 등)에 대응하는 테스트 파일이 있는가? | src/test/에 <ClassName>Test.java 생성 여부 — 새 클래스 = 새 테스트 |
```

이 단계는 기존 5단계 워크플로우에서 **Execute(3) 완료 직후, Debug(4) 시작 전** 에 위치한다.

```
Brainstorm → Plan → Execute → [역방향 추적 자가 검증] → Debug → Retro
```

### 코드 수정 내용 (#130 자체 리뷰 결함 수정)

역방향 추적 적용 결과 발견된 결함 5건을 수정했다.

| 파일 | 변경 내용 |
|---|---|
| `V8__add_unique_order_id_on_payment.sql` | `payment.order_id` UNIQUE KEY 추가 |
| `FanPaymentController.java` | `IllegalArgumentException` → `ResponseStatusException(401)` |
| `application.yml` | `spring.jackson.serialization.write-dates-as-timestamps: false` 추가 |
| `PaymentQueryServiceTest.java` | Q-1~Q-4 단위 테스트 4건 신규 작성 |
| `PaymentDetailResponse.failedAt` | 스펙("영수증·결제 복구") 의도 확인 후 유지 |

---

## Consequences — 트레이드오프

### 긍정적 효과

- **결함의 조기 탐지**: Execute 완료 직후 4개 항목을 역방향으로 추적하므로, 빌드·테스트 통과 후 코드리뷰 단계에서야 발견되던 결함이 구현 단계에서 차단된다.
- **"동작하는 코드 = 안전한 코드"** 오류 방지: 단위 테스트는 Mock을 사용하므로 DB 제약·예외 체인·직렬화 설정을 검증하지 못한다. 역방향 추적은 Mock이 감지할 수 없는 실환경 충돌을 명시적으로 탐색한다.
- **신규 클래스 테스트 공백 방지**: `새 클래스 = 새 테스트 파일` 규칙이 체크리스트로 고정되어, 특히 권한 검증 같은 보안 경로의 미검증을 방지한다.

### 부정적 효과 및 완화

- **Execute 후 소요 시간 증가**: 4개 항목 확인에 1~3분이 추가된다. 완화: 각 항목의 확인 방법(grep, 파일 열기)이 명시되어 있어 탐색 시간을 최소화할 수 있다.
- **항목 고착화**: 미래에 새 유형의 결함이 발생해도 기존 4개 항목만 확인하는 관성이 생길 수 있다. 완화: Retro 단계에서 새 패턴을 발견하면 이 체크리스트를 갱신한다(ADR-005 자기 진화 루프와 연결).

---

## Compliance — 준수 방법

| # | 규칙 |
|---|---|
| 01 | Execute 단계에서 코드 작성이 끝나면, Debug 전에 **"Execute 직후 자가 검증" 4개 항목을 순서대로 확인**한다. |
| 02 | `orElseGet(save)` 패턴 사용 시 관련 `V*.sql`을 열어 해당 컬럼의 인덱스 타입(UNIQUE vs INDEX)을 **반드시** 확인한다. |
| 03 | 새 `throw` 구문 작성 시 `@RestControllerAdvice` grep을 실행하여 핸들러 존재를 확인한다. 없으면 `ResponseStatusException`을 사용한다. |
| 04 | `Instant`, `LocalDate`, `LocalDateTime` 타입을 응답 필드로 추가할 때 `application.yml`의 `spring.jackson` 설정을 확인한다. |
| 05 | 신규 클래스(Service, Port 구현체, Controller)를 생성하면 **같은 PR** 안에 대응하는 테스트 파일을 반드시 함께 생성한다. |
| 06 | 자가 검증에서 새로운 유형의 결함 패턴이 발견되면 Retro 단계에서 이를 기록하고, 다음 튜닝 시 본 체크리스트에 추가한다. |

---

## 관련 문서

| 문서 | 경로 |
|---|---|
| AI 하네스 최초 구축 | [ADR-004](./ADR-004-ai-agent-harness-engineering.md) |
| AI 하네스 1차 튜닝 (Self-Reflection) | [ADR-005](./ADR-005-ai-harness-tuning-self-reflection.md) |
| 공통 AI 지침 (Execute 직후 자가 검증 포함) | [SHARED.md](../ai/SHARED.md) |
| 결제 상세 조회 구현 (이번 튜닝의 트리거) | GitHub Issue #130 |
| 본 튜닝 PR | GitHub PR #134 |