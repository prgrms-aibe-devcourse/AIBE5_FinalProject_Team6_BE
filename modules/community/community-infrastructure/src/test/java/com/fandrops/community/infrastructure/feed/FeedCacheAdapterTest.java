package com.fandrops.community.infrastructure.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.community.application.feed.FeedListResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedCacheAdapterTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ObjectMapper objectMapper;

    FeedCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        adapter = new FeedCacheAdapter(redisTemplate, objectMapper);
    }

    @Nested
    @DisplayName("get")
    class GetTest {

        @Test
        @DisplayName("캐시 미스 (Redis null) → Optional.empty()")
        void cacheMiss_returnsEmpty() {
            when(valueOps.get(anyString())).thenReturn(null);

            Optional<FeedListResult> result = adapter.get(10L, null, 20);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("캐시 히트 → JSON 역직렬화 후 Optional 반환")
        void cacheHit_returnsDeserializedResult() throws Exception {
            FeedListResult expected = new FeedListResult(List.of(), null, false);
            when(valueOps.get(anyString())).thenReturn("{...}");
            when(objectMapper.readValue(anyString(), eq(FeedListResult.class))).thenReturn(expected);

            Optional<FeedListResult> result = adapter.get(10L, null, 20);

            assertTrue(result.isPresent());
            assertSame(expected, result.get());
        }

        @Test
        @DisplayName("local hot cache 히트 → Redis 재조회 없이 Optional 반환")
        void localHotCacheHit_skipsRedis() throws Exception {
            FeedListResult expected = new FeedListResult(List.of(), null, false);
            when(valueOps.get(anyString())).thenReturn("{...}");
            when(objectMapper.readValue(anyString(), eq(FeedListResult.class))).thenReturn(expected);

            Optional<FeedListResult> first = adapter.get(10L, null, 20);
            Optional<FeedListResult> second = adapter.get(10L, null, 20);

            assertTrue(first.isPresent());
            assertTrue(second.isPresent());
            assertSame(expected, second.get());
            verify(valueOps, times(1)).get(anyString());
            verify(objectMapper, times(1)).readValue(anyString(), eq(FeedListResult.class));
        }

        @Test
        @DisplayName("Redis 예외 → 예외 삼킴, Optional.empty() 반환 (fail-open)")
        void redisException_returnsEmpty() {
            when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

            Optional<FeedListResult> result = adapter.get(10L, null, 20);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getOrLoad")
    class GetOrLoadTest {

        @Test
        @DisplayName("캐시 히트 → loader 미호출, 캐시 값 반환")
        void cacheHit_loaderNotCalled() throws Exception {
            FeedListResult cached = new FeedListResult(List.of(), null, false);
            when(valueOps.get(anyString())).thenReturn("{cached}");
            when(objectMapper.readValue(anyString(), eq(FeedListResult.class))).thenReturn(cached);

            @SuppressWarnings("unchecked")
            java.util.function.Supplier<FeedListResult> loader = mock(java.util.function.Supplier.class);
            FeedListResult result = adapter.getOrLoad(10L, null, 20, loader);

            assertSame(cached, result);
            verifyNoInteractions(loader);
        }

        @Test
        @DisplayName("캐시 미스 → loader 호출, Redis put, 결과 반환")
        void cacheMiss_callsLoaderAndCaches() throws Exception {
            when(valueOps.get(anyString())).thenReturn(null);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            FeedListResult loaded = new FeedListResult(List.of(), null, false);

            FeedListResult result = adapter.getOrLoad(10L, null, 20, () -> loaded);

            assertSame(loaded, result);
            verify(valueOps).set(anyString(), eq("{}"), any(Duration.class));
        }

        @Test
        @DisplayName("loader 결과 저장 후 local hot cache 히트 → loader/Redis get 미호출")
        void loadedResultStoredInLocalHotCache() throws Exception {
            when(valueOps.get(anyString())).thenReturn(null);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            FeedListResult loaded = new FeedListResult(List.of(), null, false);

            FeedListResult first = adapter.getOrLoad(10L, null, 20, () -> loaded);
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<FeedListResult> loader = mock(java.util.function.Supplier.class);
            FeedListResult second = adapter.getOrLoad(10L, null, 20, loader);

            assertSame(loaded, first);
            assertSame(loaded, second);
            verifyNoInteractions(loader);
            verify(valueOps, times(1)).get(anyString());
        }

        @Test
        @DisplayName("loader RuntimeException → inFlight 정리 후 예외 재전파")
        void loaderThrowsRuntimeException_rethrown() {
            when(valueOps.get(anyString())).thenReturn(null);

            assertThrows(RuntimeException.class,
                    () -> adapter.getOrLoad(10L, null, 20, () -> {
                        throw new RuntimeException("DB 연결 오류");
                    }));
        }

        @Test
        @DisplayName("loader Error → catch(Error)로 completeExceptionally 호출 후 재전파")
        void loaderThrowsError_completesExceptionallyAndRethrows() {
            when(valueOps.get(anyString())).thenReturn(null);

            assertThrows(OutOfMemoryError.class,
                    () -> adapter.getOrLoad(10L, null, 20, () -> {
                        throw new OutOfMemoryError("OOM test");
                    }));
        }
    }
}
