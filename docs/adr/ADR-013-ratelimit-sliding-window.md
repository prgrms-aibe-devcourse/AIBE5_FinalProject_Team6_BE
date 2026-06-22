# ADR-013: RateLimit 알고리즘 — Sliding Window Log 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-22 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) · [ADR-012 대기열 저장소](./ADR-012-queue-storage-redis-zset.md) |
| **관련** | [api-contract.md `RATE_LIMITED`](../api/api-contract.md) · [failure-policy.md §3.1](../operations/failure-policy.md) |
| **담당** | 장성재 (`payment` — RateLimit 정책) · 지영재 (Nginx/ALB 임계값 리뷰) |

---

## Title

결제·주문·대기열 진입 RateLimit은 **Sliding Window Log (Redis ZSET + Lua Script)** 로 구현한다. Fixed Window Counter, Token Bucket은 채택하지 않는다.

---

## Context

드롭스 오픈런 순간 단일 팬이 결제·주문·대기열 진입을 반복 시도할 수 있다. 이를 제한하지 않으면 특정 팬이 재고를 독점하거나 서버 자원을 소진할 수 있다.

RateLimit 적용 대상과 기준치는 다음과 같다.

| 그룹 | 엔드포인트 | 한도 | 윈도우 |
| --- | --- | --- | --- |
| `queue` | `POST /api/v1/queue/join/*` | 5회/분 | 60s |
| `order` | `POST /api/v1/orders` | 10회/분 | 60s |
| `payment` | `POST /api/v1/payments/toss/confirm` | 10회/분 | 60s |

제한 기준은 **fanId** 단위다. 미인증 요청은 Nginx IP 기반 `limit_req`가 1차 방어한다.

알고리즘 선택 시 핵심 제약은 두 가지다.

- **window 경계 버스트 방지**: 결제·주문은 순간 2배 허용이 재고 정합성에 영향을 줄 수 있다.
- **구현 단순성**: 기존 Redis(ADR-012) 재활용, 추가 인프라 없음.

---

## 방안 비교

### A안 — Fixed Window Counter

1분 단위 고정 윈도우 내 요청 수를 카운터로 관리한다. 키 예시: `ratelimit:{fanId}:{group}:{분 단위 timestamp}`.

```
00:00 ~ 01:00 윈도우: 10회 허용
01:00 ~ 02:00 윈도우: 다시 10회 허용
```

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 낮음 — `INCR` + `EXPIRE` 2개 커맨드 |
| 메모리 | 매우 낮음 — 카운터 1개 |
| 정밀도 | 낮음 — window 경계 버스트 허용 |
| 버스트 위험 | **window 끝 10회 + 새 window 시작 10회 = 1초 내 20회 허용** |
| **기각 이유** | 드롭스 오픈런에서 팬이 window 경계를 노려 순간 2배 요청을 보낼 수 있다. 결제·주문 그룹에서 10회 한도가 순간 20회로 뚫리면 재고 예약이 한도 이상으로 시도된다. 단순성 장점이 있지만 경계 버스트 취약점이 결제·재고 정합성 요구사항과 충돌한다. |

### B안 — Token Bucket

버킷에 토큰을 일정 속도로 채우고, 요청마다 토큰을 소비한다. 버킷이 비면 거부한다.

```
버킷 용량: 10 토큰
보충 속도: 10 토큰/분 (= 6초마다 1개)
```

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 높음 — 마지막 보충 시각·잔여 토큰 수를 원자적으로 관리해야 함 |
| 메모리 | 낮음 — (잔여 토큰, 마지막 보충 시각) 2개 필드 |
| 버스트 허용 | 버킷이 가득 찬 상태에서 최대 용량만큼 순간 버스트 가능 (설계에 따라 조절) |
| 정밀도 | 높음 |
| **기각 이유** | 버스트 허용 여부를 별도로 제어해야 하고, 마지막 보충 시각 기반 계산을 Lua Script 내에서 처리하는 구현 복잡도가 높다. 드롭스 use-case에서 버스트 허용이 필요하지 않으므로 복잡도 대비 이점이 없다. |

### C안 — Sliding Window Log (ZSET) ← **채택**

요청마다 타임스탬프를 ZSET에 기록하고, 조회 시 현재 시각 기준 `window` 이전 항목을 제거한 뒤 남은 수로 판단한다.

```java
// RateLimitService.java — Lua Script
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)  // 윈도우 밖 항목 제거
local count = redis.call('ZCARD', key)                // 현재 윈도우 내 요청 수
if count < limit then
    redis.call('ZADD', key, now, nonce)               // 현재 요청 기록
    redis.call('PEXPIRE', key, window)
    return 1  -- 허용
else
    return 0  -- 거부
end
```

| 관점 | 평가 |
| --- | --- |
| 구현 복잡도 | 중간 — Lua Script, ZSET 활용 |
| 메모리 | 윈도우 내 요청 수에 비례 (fanId당 최대 `limit`개 entry) |
| 버스트 허용 | 없음 — 임의 1분 구간 어디서도 항상 정확히 `limit`회만 허용 |
| window 경계 버스트 | **없음** |
| 원자성 | Lua Script — ZREMRANGEBYSCORE·ZCARD·ZADD 원자적 실행 |

---

## Decision

**C안(Sliding Window Log) 채택.**

Fixed Window의 window 경계 2배 버스트가 결제·주문 그룹에서 재고 정합성 위험을 유발할 수 있다. Sliding Window Log는 임의의 1분 구간 어디서나 정확히 `limit`회만 허용해 이 문제를 완전히 차단한다. 메모리 사용량은 `limit`값(최대 10)이 작고 ZSET entry가 `PEXPIRE`로 자동 만료되어 수용 가능하다. 기존 Redis(ADR-012)를 재활용하므로 추가 인프라가 없다.

---

## 핵심 구현 결정 3가지

### ① Lua Script — 원자적 check-and-increment

`ZREMRANGEBYSCORE`(만료 제거) → `ZCARD`(카운트 확인) → `ZADD`(기록) 3단계가 원자적으로 실행되어야 한다. 개별 커맨드로 분리하면 `ZCARD` 후 `ZADD` 사이에 다른 요청이 끼어들어 limit을 초과할 수 있다.

Lua Script는 Redis 단일 스레드에서 원자적으로 실행되므로 TOCTOU가 발생하지 않는다. ADR-012의 대기열 상태 전이와 동일한 이유다.

### ② nonce (UUID) — ZSET member 충돌 방지

ZSET에서 member는 유일해야 한다. 동일 fanId가 같은 ms에 두 번 요청하면 `ZADD`의 member가 충돌해 두 번째 요청이 기록되지 않는다. UUID를 nonce로 사용해 모든 요청이 고유 member를 가지도록 한다.

```java
UUID.randomUUID().toString()  // RateLimitService:42 — 매 요청마다 고유 member
```

### ③ fail-open — Redis 장애 시 통과

Redis 장애 시 `isAllowed()`에서 예외가 발생한다. 예외를 catch해 요청을 **통과**시킨다.

```java
// RateLimitFilter:59-62
} catch (Exception e) {
    log.warn("RateLimit Redis 오류 — fail-open 처리", e);
    // 통과 → Nginx IP 기반 limit_req가 2차 방어
}
```

fail-close(예외 시 429 거부)를 선택하면 Redis 장애가 결제·주문 전체 중단으로 직결된다. RateLimit은 보호 레이어이지 가용성 조건이 아니므로 Redis 장애 시에도 서비스가 동작해야 한다. Nginx IP 기반 `limit_req`가 최소 방어선을 담당한다.

---

## 메모리 사용량 추산

| 항목 | 계산 | 결과 |
| --- | --- | --- |
| 활성 팬 수 | 드롭스 오픈런 최대 | 2,000명 |
| 그룹 수 | queue / order / payment | 3개 |
| fanId당 최대 entry | limit 최댓값 | 10개 |
| entry 크기 | score(8B) + UUID member(36B) | ~44B |
| **총 추산** | 2,000 × 3 × 10 × 44B | **~2.5MB** |

ElastiCache 인스턴스 메모리 대비 무시할 수준이다. `PEXPIRE`로 window(60s) 경과 후 자동 만료되어 누적되지 않는다.

---

## Consequences

### 긍정

- window 경계 버스트 없음 — 결제·주문·대기열 모든 구간에서 정확히 `limit`회 적용
- Lua Script 원자적 실행 — 동시 요청에도 limit 초과 없음
- 기존 Redis 재활용 — 추가 인프라 없음
- `PEXPIRE`로 자동 만료 — 별도 정리 불필요

### 부정 · 수용

- Fixed Window 대비 메모리 사용량 증가 → 추산 ~2.5MB, 수용 가능
- Redis 장애 시 RateLimit 비활성화 → fail-open + Nginx 2차 방어로 수용

### 불변

| 규칙 | 내용 |
| --- | --- |
| 제한 기준 | **fanId** 단위 — IP 기반 아님 (멀티 탭·프록시 우회 방지) |
| 응답 계약 | `429 + retryable: true + Retry-After` 헤더 필수 (`api-contract.md`) |
| 미인증 요청 | RateLimit 스킵 → Nginx `limit_req` 위임 |
| fail-open | Redis 장애 시 통과 — fail-close 금지 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| RateLimit 에러 계약 | [api-contract.md](../api/api-contract.md) |
| Redis 장애 정책 | [failure-policy.md §3.1](../operations/failure-policy.md) |
| 대기열 저장소 | [ADR-012](./ADR-012-queue-storage-redis-zset.md) |