# ADR-017: 출석 체크 달성 조건 — 총 7회 (비연속)

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-05-28 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) |
| **관련** | — |
| **담당** | 정환철 (`community`) |

---

## Title

`AttendanceEvent`의 리워드 달성 조건을 **연속 7일**이 아닌 **총 7회(비연속)** 로 정의하고, `streakDays` 필드명은 내부 구현 용어로 유지하되 **총 체크인 횟수**임을 코드 주석으로 명시한다.

---

## Context

출석 체크 기능은 아티스트가 팬을 대상으로 이벤트 기간(startDate ~ endDate) 내에 리워드를 제공하는 수단이다. 팬이 하루 1회 체크인할 수 있으며, 7회 달성 시 `rewardDesc`에 정의된 리워드를 받는다.

팀 설계 초기 "7일 연속 출석 보상"이라는 표현이 사용되어 `streakDays`라는 필드명이 사용됐다. streak는 통상 **연속** 을 의미하므로, 실제 동작(총 누적 횟수)과 이름 사이에 불일치가 있었다.

또한 같은 팬이 하루에 두 번 체크인을 시도하는 경우를 막기 위해 DB 유니크 제약 `(event_id, fan_id, checked_date)`이 이미 적용되어 있어, 날짜 기반 연속성 추적 없이도 중복 방지가 보장된다.

---

## 방안 비교

### A안 — 연속 7일 (streak)

매 체크인 시 직전 날짜와의 연속성을 검사하고, 중단되면 카운터를 0으로 리셋한다.

| 관점 | 평가 |
| --- | --- |
| UX | 하루 빠지면 처음부터 — 이탈 유도 우려 |
| 구현 복잡도 | 마지막 체크인 날짜 조회 + 비교 로직 필요 |
| DB 부하 | 마지막 체크인 날짜 별도 조회 필요 |
| **기각 이유** | MVP 단계에서 페널티가 강한 연속 조건은 신규 팬 이탈을 높인다. 팀 합의로 기각. |

### B안 — 총 7회(비연속) ← **채택**

이벤트 기간 내 누적 체크인 수(`countByEventIdAndFanId`)만 추적하고, 7에 도달하면 달성으로 판단한다. 순서·연속성은 묻지 않는다.

| 관점 | 평가 |
| --- | --- |
| UX | 하루 쉬어도 카운터 유지 — 부담 없는 참여 |
| 구현 복잡도 | `COUNT` 쿼리 하나로 해결 |
| DB 부하 | 단순 집계 — 인덱스 `(event_id, fan_id)` 로 커버 |
| 단점 | 이벤트 기간이 짧을 경우(7일 미만) 사실상 달성 불가 → 기획 단에서 기간 관리 필요 |

### C안 — 주간 단위 7/7 (매주 7일 연속)

이벤트를 주(week) 단위로 쪼개 각 주에 7일 모두 출석해야 달성으로 인정한다.

| 관점 | 평가 |
| --- | --- |
| 복잡도 | 주 경계 계산, 복잡한 집계 |
| UX | A안보다 더 엄격 |
| **기각 이유** | 구현 복잡도 대비 팬 경험 개선이 없다. 만장일치 기각. |

---

## Decision

**B안(총 7회, 비연속) 채택.**

`AttendanceLogRepository.countByEventIdAndFanId(eventId, fanId)`로 누적 횟수를 조회하고, 그 값을 `CheckInResult.streakDays`로 반환한다. 클라이언트(FE/앱)가 7에 도달했을 때 리워드 안내를 표시한다.

---

## 핵심 구현 결정 2가지

### ① streakDays 명명 유지 + 코드 주석으로 의미 명확화

`streakDays`는 기존 API 계약(클라이언트·FE 이미 사용)과의 하위 호환을 위해 필드명을 유지하되, `AttendanceService`에 의미를 명시적으로 주석 처리했다.

```java
// 연속 일수가 아닌 총 체크인 횟수 (팀 합의: 총 7회 달성 시 리워드)
// countByEventIdAndFanId() = 해당 이벤트에서 팬의 총 체크인 수
int streakDays = logRepository.countByEventIdAndFanId(eventId, fanId);
return new CheckInResult(event.getId(), log.checkedDate(), streakDays);
```

이벤트 응답 DTO에도 동일 필드명이 사용된다.

```java
public record CheckInResult(
        Long eventId,
        LocalDate checkedDate,
        int streakDays   // 이벤트 내 팬의 총 체크인 횟수
) {}
```

### ② 중복 방지 — DB 유니크 제약으로만 보장

연속성 추적이 없으므로 "같은 날 두 번 체크인"만 막으면 된다. `attendance_log` 테이블에 `UNIQUE(event_id, fan_id, checked_date)` 제약을 두어, 애플리케이션 레이어 검증이 실패하더라도 DB가 마지막 방어선이 된다.

애플리케이션에서도 `DuplicateAttendanceException`을 던지기 전에 먼저 체크하여 사용자에게 명확한 메시지를 제공한다.

---

## Consequences

### 긍정

- 구현 단순 — 집계 쿼리 1개, 날짜 비교 로직 없음
- 팬 UX 개선 — 하루 빠져도 진행 상황 유지, 중단 없는 참여
- `attendance_log` UNIQUE 제약이 연속 추적 없이도 중복 방지 보장

### 부정 · 수용

- `streakDays` 필드명이 "연속"을 연상시켜 오해 여지 있음 → 코드 주석으로 명시. 외부 API 명칭 변경은 클라이언트 수정 비용이 있어 유지.
- 이벤트 기간 7일 미만이면 달성 불가 → 기획 단에서 최소 기간 7일 이상 보장 필요

### 불변

| 규칙 | 내용 |
| --- | --- |
| 달성 조건 | 총 7회(비연속) — 연속 스트릭으로 변경 금지 |
| DB 제약 | `UNIQUE(event_id, fan_id, checked_date)` — 제거 금지 |
| 필드명 | `streakDays` — 외부 API 계약 유지, 내부 의미를 코드 주석으로 보완 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 멀티모듈 구조 | [ADR-001](./ADR-001-multi-module-monolith.md) |