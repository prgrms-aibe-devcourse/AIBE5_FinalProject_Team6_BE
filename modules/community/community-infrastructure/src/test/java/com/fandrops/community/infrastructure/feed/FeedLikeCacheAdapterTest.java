package com.fandrops.community.infrastructure.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedLikeCacheAdapterTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    FeedLikeCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new FeedLikeCacheAdapter(redisTemplate, new ObjectMapper());
    }

    @Nested
    @DisplayName("getOrLoad")
    class GetOrLoadTest {

        @Test
        @DisplayName("캐시 hit — loader 미실행, 캐시된 Set 반환")
        void cacheHit_returnsFromCacheWithoutCallingLoader() throws Exception {
            when(valueOps.get(anyString())).thenReturn("[1,3]");

            Set<Long> result = adapter.getOrLoad(42L, List.of(1L, 2L, 3L),
                    () -> { throw new RuntimeException("loader must not be called"); });

            assertEquals(Set.of(1L, 3L), result);
            verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("local hot cache hit — Redis 재조회 없이 캐시된 Set 반환")
        void localHotCacheHit_skipsRedis() throws Exception {
            when(valueOps.get(anyString())).thenReturn("[1,3]");

            Set<Long> first = adapter.getOrLoad(42L, List.of(1L, 2L, 3L), Set::of);
            Set<Long> second = adapter.getOrLoad(42L, List.of(1L, 2L, 3L), Set::of);

            assertEquals(Set.of(1L, 3L), first);
            assertEquals(Set.of(1L, 3L), second);
            verify(valueOps, times(1)).get(anyString());
        }

        @Test
        @DisplayName("캐시 miss — loader 실행 후 Redis에 저장")
        void cacheMiss_invokesLoaderAndStoresResult() {
            when(valueOps.get(anyString())).thenReturn(null);

            Set<Long> result = adapter.getOrLoad(42L, List.of(1L, 2L), () -> Set.of(1L));

            assertEquals(Set.of(1L), result);
            verify(valueOps).set(anyString(), anyString(), eq(Duration.ofSeconds(30)));
        }

        @Test
        @DisplayName("loader 결과 저장 후 local hot cache hit — loader/Redis get 미호출")
        void loadedResultStoredInLocalHotCache() {
            when(valueOps.get(anyString())).thenReturn(null);

            Set<Long> first = adapter.getOrLoad(42L, List.of(1L, 2L), () -> Set.of(1L));
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<Set<Long>> loader = mock(java.util.function.Supplier.class);
            Set<Long> second = adapter.getOrLoad(42L, List.of(1L, 2L), loader);

            assertEquals(Set.of(1L), first);
            assertEquals(Set.of(1L), second);
            verifyNoInteractions(loader);
            verify(valueOps, times(1)).get(anyString());
        }

        @Test
        @DisplayName("feedIds 빈 목록 — Redis 미호출, 빈 Set 즉시 반환")
        void emptyFeedIds_returnsEmptySetWithoutRedisCall() {
            Set<Long> result = adapter.getOrLoad(42L, List.of(), () -> Set.of(99L));

            assertTrue(result.isEmpty());
            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("Redis get 예외 — fail-open: loader 실행 후 결과 반환")
        void redisGetException_failOpen_callsLoader() {
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis timeout"));

            Set<Long> result = adapter.getOrLoad(42L, List.of(1L), () -> Set.of(1L));

            assertEquals(Set.of(1L), result);
        }

        @Test
        @DisplayName("Redis put 예외 — loader 결과 정상 반환, 예외 미전파")
        void redisPutException_stillReturnsResult() {
            when(valueOps.get(anyString())).thenReturn(null);
            doThrow(new RuntimeException("put failed")).when(valueOps)
                    .set(anyString(), anyString(), any(Duration.class));

            Set<Long> result = adapter.getOrLoad(42L, List.of(1L), () -> Set.of(1L));

            assertEquals(Set.of(1L), result);
        }

        @Test
        @DisplayName("feedIds 정렬 후 키 생성 — 입력 순서 무관하게 동일 키")
        void key_builtFromSortedFeedIds() {
            when(valueOps.get("feed:liked:42:1,2,3")).thenReturn("[1]");

            // 역순 입력 → 정렬 후 같은 키 사용
            adapter.getOrLoad(42L, List.of(3L, 1L, 2L), () -> Set.of());

            verify(valueOps).get("feed:liked:42:1,2,3");
        }
    }

    @Nested
    @DisplayName("evictByFanId")
    class EvictByFanIdTest {

        @Test
        @DisplayName("Redis execute 예외 — fail-open: 예외 미전파")
        void redisException_doesNotPropagate() {
            when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(new RuntimeException("Redis down"));

            assertDoesNotThrow(() -> adapter.evictByFanId(42L));
        }
    }
}
