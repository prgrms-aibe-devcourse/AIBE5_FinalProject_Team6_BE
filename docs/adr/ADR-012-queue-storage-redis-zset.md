# ADR-012: 대기열 저장소 전략 — Redis ZSET 채택

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-22 |
| **선행 ADR** | [ADR-001 멀티모듈 모놀리스](./ADR-001-multi-module-monolith.md) · [ADR-010 대기열 SSE](./ADR-010-queue-realtime-notification-sse.md) |
| **관련** | [invariants §6 WAIT_QUEUE](../state/invariants-and-state-machines.md) · [ERD §10 대기열](../erd/erd-design.md) · [data-retention-and-audit-policy.md](../erd/data-retention-and-audit-policy.md) |
| **담당** | 장성재 (`payment` — 대기열·Access Ticket) |

---

## Title

드롭스 대기열의 팬 순번·상태를 **Redis ZSET + Hash + Lua Script** 조합으로 저장한다. RDB(MySQL), Kafka는 채택하지 않는다.

---

## Context

드롭스 오픈런 시 대기열 저장소는 아래 요구사항을 동시에 만족해야 한다.

- **진입 순서 보장**: 먼저 들어온 팬이 앞 번호를 받아야 한다.
- **순위 조회**: 특정 팬의 현재 순번을 빠르게 반환해야 한다 (`GET /queue/status`).
- **원자적 상태 전이**: `WAITING → PROCESSING`, `PROCESSING → DONE/EXPIRED` 전이 중 동시 요청으로 인한 중복 처리(TOCTOU)를 막아야 한다.
- **자동 만료**: 드롭스 종료 후 대기열 데이터가 자동으로 정리되어야 한다.
- **일시적 데이터**: 대기열 데이터는 드롭스 기간(최대 24시간)에만 유효하다. 영구 보관이 필요하지 않다.

---

## 방안 비교

### A안 — RDB (MySQL)

대기열 상태를 `wait_queue` 테이블에 저장하고, 순위는 `ORDER BY joined_at`·`ROW_NUMBER()`로 계산한다.

```sql
-- 순위 조회 예시
SELECT rank FROM (
    SELECT fan_id, ROW_NUMBER() OVER (ORDER BY joined_at) AS rank
    FROM wait_queue WHERE product_id = ? AND status = 'WAITING'
) t WHERE fan_id = ?;
```

| 관점 | 평가 |
| --- | --- |
| 순위 조회 성능 | `ROW_NUMBER()` 매 조회마다 전체 스캔 → 대기자 수에 비례해 비용 증가 |
| 원자적 전이 | `SELECT FOR UPDATE` 또는 낙관적 락 — 드롭스 집중 구간 커넥션 풀 점유 |
| 자동 만료 | TTL 없음 → 별도 만료 Job 필요 |
| 영속성 | 강함 — 재시작 후에도 데이터 유지 |
| 인프라 | 별도 추가 없음 (기존 MySQL) |
| **기각 이유** | 대기열 순위 조회는 SSE tick마다(3초), 최대 2,000 팬 기준 `ROW_NUMBER()` 반복 실행이 MySQL에 지속적인 부하를 준다. 대기열 데이터는 드롭스 기간만 유효한 일시적 데이터인데 RDB의 영속성 비용을 지불할 이유가 없다. 만료 Job 추가 운영 부담도 발생한다. |

### B안 — Kafka (토픽 기반 큐)

`queue-join` 토픽에 진입 이벤트를 발행하고, 컨슈머가 순서대로 처리한다.

| 관점 | 평가 |
| --- | --- |
| 순서 보장 | 파티션 내 순서 보장 — 강력 |
| 순위 조회 | Consumer offset으로는 특정 팬의 현재 순번 조회 불가 → 별도 상태 저장소 필요 |
| 자동 만료 | 토픽 retention으로 설정 가능 |
| 인프라 | **별도 Kafka 클러스터 필요** |
| Consumer Lag | 처리 지연 시 lag 누적 → 순위 정보 부정확 |
| **기각 이유** | ADR-001에서 Kafka는 MVP 스코프 밖(Not Scope)으로 명시. 순위 조회를 위한 별도 상태 저장소가 추가로 필요해 결국 Redis나 RDB를 함께 써야 한다. 인프라 복잡도 대비 이점이 없다. |

### C안 — Redis ZSET + Hash + Lua Script ← **채택**

| 자료구조 | 역할 | 키 패턴 |
| --- | --- | --- |
| ZSET | 대기 순번 (score = joinedAt ms) | `queue:{productId}:waiting` |
| ZSET | PROCESSING 팬 목록 (score = processingStartAt ms) | `queue:{productId}:processing` |
| Hash | 팬별 상태 (`status`, `joinedAt`, `queueId` 등) | `queue:{productId}:fan:{fanId}` |

| 관점 | 평가 |
| --- | --- |
| 순위 조회 | `ZRANK` O(log N) — 대기자 수와 무관하게 일정한 성능 |
| 원자적 전이 | Lua Script — Redis 단일 스레드 실행 보장, TOCTOU 없음 |
| 자동 만료 | `EXPIRE`로 TTL 설정 (기본 86400s = 24h) → 드롭스 종료 후 자동 정리 |
| 영속성 | 인메모리 — JVM/Redis 재시작 시 손실 가능 |
| 인프라 | 기존 ElastiCache 활용 (ADR-001 Redis 용도 일치) |
| 단점 | Redis 장애 시 대기열 데이터 손실 → fail-open 정책으로 수용 (`failure-policy.md §3.1`) |

---

## Decision

**C안(Redis ZSET) 채택.**

ZSET의 score 기반 정렬이 `joinedAt` 순번 관리 use-case와 정확히 부합한다. `ZRANK` O(log N)으로 순위 조회 성능이 대기자 수에 무관하게 일정하고, Lua Script로 `WAITING → PROCESSING` 전이를 원자적으로 처리해 TOCTOU를 방지한다. 대기열 데이터는 드롭스 기간만 유효한 일시적 데이터이므로 Redis 인메모리 특성이 오히려 적합하며, TTL로 드롭스 종료 후 자동 정리된다.

---

## 핵심 구현 결정 3가지

### ① Lua Script — 원자적 상태 전이

Redis는 단일 스레드이지만, 여러 커맨드를 개별 호출하면 그 사이에 다른 커맨드가 끼어들 수 있다(TOCTOU). 드롭스 오픈런 순간 동일 팬이 중복 진입하거나 `QueueAdvanceScheduler`의 tick과 클라이언트 요청이 동시에 상태를 전이하려 하면 정합성이 깨진다.

Lua Script는 Redis에서 **원자적으로 실행**된다. `HGET`으로 현재 상태를 확인하고 `ZREM/ZADD/HMSET/EXPIRE`까지 중간에 다른 커맨드가 끼어들 수 없다.

```java
// WAITING → PROCESSING 원자적 전이 (RedisWaitQueueRepository:64-73)
private static final RedisScript<Long> ADVANCE_SCRIPT = RedisScript.of(
    "local s = redis.call('HGET', KEYS[3], 'status') " +
    "if s ~= 'WAITING' then return 0 end " +          // 상태 체크
    "redis.call('ZREM', KEYS[1], ARGV[1]) " +          // waiting 셋에서 제거
    "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1]) " + // processing 셋에 추가
    "redis.call('HMSET', KEYS[3], 'status', 'PROCESSING', ...) " +
    "redis.call('EXPIRE', ...) " +
    "return 1",
    Long.class);
```

### ② ZSET score = joinedAt ms — 순번 정렬 기준

`joinedAt` 밀리초 타임스탬프를 score로 사용한다. score 오름차순 정렬로 먼저 들어온 팬이 낮은 순번(앞 번호)을 받는다. `ZRANK`는 0-based index를 반환하므로 `+1`해서 1-based 순번으로 변환한다.

```java
// RedisWaitQueueRepository:144-147
public long getPosition(Long fanId, Long productId) {
    Long rank = redisTemplate.opsForZSet().rank(waitingKey(productId), String.valueOf(fanId));
    return rank != null ? rank + 1 : -1L;  // 0-based → 1-based
}
```

동일 ms에 여러 팬이 진입하면 Redis 내부 사전순으로 tiebreak된다. 드롭스 오픈런에서 ms 단위 동시 진입이 발생할 수 있으나, 어느 순서든 결과는 공정한 선착순으로 수용한다.

### ③ TTL로 대기열 자동 만료 — 별도 정리 Job 없음

`EXPIRE`로 WAITING/PROCESSING ZSET과 팬별 Hash에 동일한 TTL(기본 86400s)을 설정한다. 드롭스 종료 후 24시간이 지나면 Redis가 자동으로 삭제한다.

```java
// JOIN_SCRIPT 中
"redis.call('EXPIRE', KEYS[1], ARGV[5]) " +  // waiting ZSET TTL
"redis.call('EXPIRE', KEYS[2], ARGV[5]) " +  // fan Hash TTL
```

RDB였다면 별도 만료 Job을 작성하고 운영해야 했다. Redis TTL이 이를 대체해 운영 부담을 줄인다.

---

## 인프라 제약 사항

Redis는 인메모리 저장소이므로 대용량 데이터를 장기간 보관하기에 무리가 있다. 대기열 데이터가 이에 해당하지 않도록 두 가지 정책을 적용한다.

| 정책 | 내용 |
| --- | --- |
| TTL 강제 | 모든 대기열 Key에 `EXPIRE` 필수 — TTL 없는 Key 생성 금지 |
| 드롭스 1개 기준 최대 크기 | WAITING ZSET: 팬 수 × 8bytes(score) + member 크기 ≈ 수십 KB 이내 |

Redis 장애 시 대기열 데이터가 손실될 수 있다. 이 경우 진행 중인 드롭스는 운영 재개 불가 → P1 장애로 처리한다 (`failure-policy.md §3.1`).

---

## Consequences

### 긍정

- `ZRANK` O(log N) — 대기자 수 증가에도 일정한 순위 조회 성능
- Lua Script 원자적 전이 — 상태 전이 TOCTOU 방지
- TTL 자동 만료 — 만료 Job 없이 드롭스 종료 후 자동 정리
- 기존 ElastiCache 활용 — 추가 인프라 없음 (ADR-001 Redis 용도 범위 내)

### 부정 · 수용

- Redis 장애 시 대기열 데이터 손실 → P1 장애 처리, fail-open 정책 수용 (`failure-policy.md §3.1`)
- 동일 ms 진입 팬의 순번 사전순 tiebreak → 오픈런 공정성 범위 내 수용
- `SCAN` 기반 `findActiveProductIds()` — 운영 중 대규모 Key 공간에서 커서 반복 비용 발생 → count 100 배치로 부하 분산

### 불변

| 규칙 | 내용 |
| --- | --- |
| TTL | 모든 대기열 Key에 `EXPIRE` 설정 필수 |
| 상태 전이 | Lua Script를 통한 원자적 처리만 허용 — 개별 커맨드 분리 금지 |
| 데이터 보관 정책 | 대기열 TTL은 `data-retention-and-audit-policy.md` 기준 준수 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| 대기열 불변식 | [invariants-and-state-machines.md §6](../state/invariants-and-state-machines.md) |
| Redis 장애 정책 | [failure-policy.md §3.1](../operations/failure-policy.md) |
| 대기열 TTL 정책 | [data-retention-and-audit-policy.md](../erd/data-retention-and-audit-policy.md) |
| 대기열 SSE | [ADR-010](./ADR-010-queue-realtime-notification-sse.md) |