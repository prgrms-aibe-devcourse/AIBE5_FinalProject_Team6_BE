# ADR-018: 팬 좋아요 상태 Redis 캐싱 — feed:liked:{fanId} 분리 전략

| 항목 | 내용 |
| --- | --- |
| **상태** | Accepted |
| **결정일** | 2026-06-20 |
| **선행 ADR** | [ADR-012 큐 저장소 Redis ZSet](./ADR-012-queue-storage-redis-zset.md) · [ADR-013 FeedCache viewer-agnostic 전략](./ADR-013-feed-cache-viewer-agnostic.md) |
| **관련** | [k6-tuned-results.md § s02](../operations/k6/k6-tuned-results.md) |
| **담당** | 정환철 (`community`) |
| **관련 PR** | [#394](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/394) · [#463](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/463) |

---

## Title

피드 목록 조회 시 `applyIsLiked()` DB 쿼리를 제거하기 위해, 팬이 좋아요를 누른 feedId 집합을 `feed:liked:{fanId}:{sortedFeedIds}` 키로 별도 캐싱한다. viewer-agnostic FeedCache와 독립된 TTL 30s를 적용하고, 좋아요/취소 시 SCAN+DEL로 evict한다.

---

## Context

ADR-013에서 피드 목록은 viewer-agnostic(`community:feed:{artistId}:cursor:*`)으로 캐싱되어 팬 전체가 공유한다. 그러나 `isLiked` 플래그(해당 팬이 각 피드에 좋아요를 눌렀는지 여부)는 팬마다 다르므로 viewer-agnostic 캐시에 포함할 수 없다.

기존 구현에서는 FeedCache 히트 시에도 `feedLikeRepository.findLikedFeedIdsByFanId(fanId, feedIds)` DB 쿼리가 매 요청마다 실행됐다. k6 s02 부하 테스트(VU 50, 2분)에서 이 쿼리가 병목이 되어 P95 168~172ms를 기록했다(SLO 120ms 미달).

제거 조건:
- 피드 목록 feedIds는 FeedCache TTL(60~90s) 동안 결정적(deterministic)으로 유지됨
- 동일 VU가 동일 페이지를 반복 요청하면 feedIds가 동일 → 좋아요 캐시 키 안정

---

## 방안 비교

### A안 — viewer-agnostic FeedCache에 isLiked 포함

팬별 `isLiked` 맵을 FeedCache 내부에 내포하여 단일 캐시 히트로 모두 해결한다.

| 관점 | 평가 |
| --- | --- |
| 요청 수 | Redis 1회 |
| 캐시 효율 | viewer-agnostic 불가 — 팬 수만큼 캐시 복사 발생 |
| 메모리 | 팬당 피드 페이지 수 × 캐시 엔트리 — 폭발적 증가 |
| **기각 이유** | ADR-013의 viewer-agnostic 공유 캐시 원칙을 완전히 파괴한다. 팬 1만 명이 같은 아티스트 피드를 보면 캐시 엔트리가 1만 배 증가한다. |

### B안 — 팬별 Redis Set: `feed:liked:{fanId}` (전체 좋아요 목록)

한 팬의 모든 좋아요 feedId를 Redis Set 하나에 저장하고, 피드 목록 조회 시 SINTER로 교집합을 구한다.

| 관점 | 평가 |
| --- | --- |
| 캐시 크기 | 팬이 좋아요한 전체 피드 수 누적 — 장기 팬은 Set이 커짐 |
| 갱신 복잡도 | likeFeed 시 SADD, unlike 시 SREM — 단순 |
| 조회 | SINTER(feedIds, `feed:liked:{fanId}`) |
| **기각 이유** | 활성 팬의 전체 좋아요 Set은 수천 건 이상으로 커질 수 있고, TTL 관리 없이 영구 누적될 수 있다. 현재 피드 페이지에 표시되지 않는 feedId까지 캐시에 보유하는 것은 낭비다. |

### C안 — 페이지 단위 String 캐시: `feed:liked:{fanId}:{sortedFeedIds}` ← **채택**

현재 피드 페이지에 표시되는 feedIds를 정렬·결합해 캐시 키로 삼고, 해당 팬이 좋아요한 feedId의 `Set<Long>`을 JSON으로 직렬화해 저장한다.

```
feed:liked:{fanId}:{sortedFeedIds}  →  "[1,3]"  (TTL 30s)
```

| 관점 | 평가 |
| --- | --- |
| 캐시 크기 | 페이지당 feedIds 개수 × 팬 수 — 제한적 |
| 키 안정성 | FeedCache TTL(60~90s) 동안 동일 feedIds → 반복 요청 캐시 히트 |
| TTL | 30s — like/unlike 이벤트 없어도 자동 만료 |
| evict | like/unlike 시 SCAN(`feed:liked:{fanId}:*`) + DEL |
| 단점 | 같은 팬이 여러 페이지를 보면 페이지당 캐시 엔트리 1개씩 생성 |

---

## Decision

**C안(페이지 단위 String 캐시) 채택.**

FeedCache가 viewer-agnostic으로 공유되는 동안 like 상태 캐시는 팬×페이지 단위로 분리해야 한다. feedIds를 정렬·결합한 키로 페이지 캐시를 특정하면, 같은 페이지 반복 조회에서 100% 캐시 히트를 달성한다. TTL 30s는 좋아요 반응성(최대 30s 지연 노출)과 메모리 비용 사이의 합리적 균형이다.

---

## 핵심 구현 결정 4가지

### ① FeedLikeCachePort — application 레이어 포트 격리

Redis 구현을 `community-application`에 노출하지 않기 위해 포트 인터페이스를 선언한다. `domain` 레이어 금지 규칙에 따라 `application` 레이어에 위치한다.

```java
// community-application/port/FeedLikeCachePort.java
public interface FeedLikeCachePort {
    Set<Long> getOrLoad(Long fanId, List<Long> feedIds, Supplier<Set<Long>> loader);
    void evictByFanId(Long fanId);
}
```

`FeedLikeCacheAdapter`(community-infrastructure)가 구현하며, `@Component`로 자동 감지된다.

### ② 캐시 키 — 정렬된 feedIds 결합

```java
// FeedLikeCacheAdapter.java
private String buildKey(Long fanId, List<Long> feedIds) {
    String ids = feedIds.stream().sorted().map(String::valueOf)
                         .collect(Collectors.joining(","));
    return KEY_PREFIX + fanId + ":" + ids;  // "feed:liked:42:1,2,3"
}
```

FeedCache에서 반환되는 feedIds는 커서 기반 페이지네이션으로 결정적이므로, 같은 페이지 요청은 입력 순서와 무관하게 항상 동일 키를 사용한다.

### ③ evict 방식 — SCAN+DEL (like/unlike 직후 직접 호출)

`@TransactionalEventListener(AFTER_COMMIT)` 리스너 패턴(PR #368 FeedCacheEvictListener 참고)은 JDK 프록시 복잡도를 유발하므로, `@Transactional` 메서드 내에서 DB 저장 직후 직접 호출한다. evict 실패 시 최대 TTL(30s) 동안 이전 상태 노출을 허용한다(blast radius 허용 범위).

```java
// FeedLikeService.likeFeed()
feedLikeRepository.save(FeedLike.byFan(feedId, fanId, artistId, clock));
// evict 실패 시 최대 30s(TTL) 동안 이전 좋아요 목록 반환 허용
// like/unlike는 DB 커밋 완료 후이므로 정합성에 영향 없음
feedLikeCachePort.evictByFanId(fanId);
```

evict 구현에서는 SCAN(count=100)으로 `feed:liked:{fanId}:*` 패턴을 스캔 후 일괄 DEL한다. 팬 1명당 활성 키는 1~2개로 SCAN 비용이 무해하나, keyspace 규모 증가 시 재검토가 필요하다.

### ④ JVM local hot cache — 2초 인메모리 레이어 (PR #463)

s06 통합 부하에서 feed·queue·order·payment 트래픽이 단일 Redis 인스턴스에 동시 집중되면서 `FeedLikeCacheAdapter`의 Redis GET + JSON 역직렬화 꼬리 지연이 확대됐다. `FeedCacheAdapter`와 동일한 패턴으로 `FeedLikeCacheAdapter`에 `LocalEntry<T>` record 기반 2초 JVM 캐시 레이어를 추가해 반복 요청의 Redis 호출을 생략한다.

- 캐시 계층: **JVM 2s → Redis 30s → DB** 3단 레이어
- evict: `evictByFanId()` 내에서 `localCache.keySet().removeIf()` 선행 후 Redis SCAN+DEL 후속

```java
private final ConcurrentHashMap<String, LocalEntry<Set<Long>>> localCache = new ConcurrentHashMap<>();

private record LocalEntry<T>(T value, long expiresAtNanos) {
    boolean isExpired() { return System.nanoTime() >= expiresAtNanos; }
}

@Override
public void evictByFanId(Long fanId) {
    localCache.keySet().removeIf(key -> key.startsWith(KEY_PREFIX + fanId + ":"));
    // Redis SCAN+DEL 후속 ...
}
```

TTL 만료(`isExpired()`)를 확인해 stale 엔트리를 제거한 뒤 Redis를 조회하며, Redis miss 시에만 loader(DB)를 실행한다. 2초 TTL 동안 stale 허용 범위는 FeedCache(2s)와 동일하다.

---

## 인프라 제약

| 항목 | 값 |
| --- | --- |
| JVM local TTL | 2s (`LocalEntry<Set<Long>>`, `ConcurrentHashMap` — PR #463 추가) |
| local evict | `evictByFanId()` 시 `localCache.keySet().removeIf()` 선행 |
| Redis TTL | 30s (FeedCache 60~90s의 절반 이하 — FeedCache 만료 전 자동 만료) |
| evict 트리거 | `FeedLikeService.likeFeed()` · `unlikeFeed()` 직접 호출 |
| 키 패턴 | `feed:liked:{fanId}:{sortedFeedIds}` |
| 직렬화 | Jackson `ObjectMapper`, `Set<Long>` JSON |
| fail-open | Redis 장애 시 loader(DB 쿼리)로 폴백, 예외 미전파 |

---

## Consequences

### 긍정

- FeedCache 히트 시 `feedLikeRepository.findLikedFeedIdsByFanId()` DB 쿼리 0회
  - s02 real-final P95 **66.47ms ✅** (SLO 120ms 달성) — 적용 전 276.44ms 대비 75.9% 개선
  - 처리량 311 RPS → **1,617 RPS** (5.2배)
- JVM local hot cache(2s) 추가(PR #463) 후 s06 통합 부하 feed P95 **58.84ms ✅** — 적용 전 187.66ms 대비 68.6% 개선
- viewer-agnostic FeedCache 구조 유지 — 아티스트 피드 캐시를 팬별로 복제하지 않음
- Redis 장애 시 fail-open — DB로 자동 폴백, 서비스 중단 없음

### 부정 · 수용

- like/unlike와 cache evict가 같은 트랜잭션 내에 있으므로 DB 롤백 시 evict 불필요한데도 실행될 수 있음. 단, TTL 30s 내 자연 만료로 허용.
- 팬이 여러 아티스트의 피드를 동시에 탐색하면 페이지당 캐시 엔트리 1개씩 생성 — 메모리 분산 허용.
- SCAN+DEL 기반 evict — 현재 팬당 활성 키 1~2개로 무해하나, keyspace 급증 시 재검토 필요 (TODO 주석 표시).

### 불변

| 규칙 | 내용 |
| --- | --- |
| 레이어 방향 | `FeedLikeCachePort`는 `community-application`에 — domain에 Redis 타입 금지 |
| viewer-agnostic 분리 | FeedCache와 like 캐시는 별도 키 네임스페이스 — 통합 금지 |
| fail-open | 모든 Redis 연산은 try-catch — 예외 전파 금지 |
| evict 트리거 | like/unlike 성공 후 반드시 `evictByFanId` 호출 |

---

## 관련 문서

| 문서 | 경로 |
| --- | --- |
| FeedCache viewer-agnostic 전략 | [ADR-013](./ADR-013-feed-cache-viewer-agnostic.md) |
| Redis ZSet 큐 패턴 | [ADR-012](./ADR-012-queue-storage-redis-zset.md) |
| k6 s02 초기 측정 결과 | [k6-tuned-results.md](../operations/k6/k6-tuned-results.md) |
| k6 s02 final 측정 결과 | [k6-final-results.md](../operations/k6/k6-final-results.md) |
| k6 s02·s06 real-final 측정 결과 | [k6-realfinal-result.md](../operations/k6/k6-realfinal-result.md) |
| FeedLikeCache 구현 PR | [#394](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/394) |
| JVM local hot cache 추가 PR | [#463](https://github.com/prgrms-aibe-devcourse/AIBE5_FinalProject_Team6_BE/pull/463) |
