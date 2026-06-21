package com.fandrops.community.infrastructure.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.community.application.feed.FeedListResult;
import com.fandrops.community.application.port.FeedCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class FeedCacheAdapter implements FeedCachePort {

    private static final Logger log = LoggerFactory.getLogger(FeedCacheAdapter.class);
    private static final String KEY_PREFIX = "community:feed:";
    private static final Duration BASE_TTL = Duration.ofSeconds(60);
    private static final int JITTER_MAX_SECONDS = 30;
    private static final int SINGLEFLIGHT_TIMEOUT_SECONDS = 5;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    // SingleFlight: 동일 캐시 키에 대한 동시 DB 쿼리를 한 번으로 줄임
    private final ConcurrentHashMap<String, CompletableFuture<FeedListResult>> inFlight = new ConcurrentHashMap<>();

    public FeedCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FeedListResult> get(Long artistId, Long cursorId, int size) {
        String key = buildKey(artistId, cursorId, size);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, FeedListResult.class));
        } catch (Exception e) {
            log.warn("[FeedCache] get failed key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(Long artistId, Long cursorId, int size, FeedListResult result) {
        String key = buildKey(artistId, cursorId, size);
        try {
            String json = objectMapper.writeValueAsString(result);
            int jitter = ThreadLocalRandom.current().nextInt(JITTER_MAX_SECONDS + 1);
            Duration ttl = BASE_TTL.plusSeconds(jitter);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("[FeedCache] put failed key={}", key, e);
        }
    }

    @Override
    public FeedListResult getOrLoad(Long artistId, Long cursorId, int size, Supplier<FeedListResult> loader) {
        // 1. 캐시 조회
        Optional<FeedListResult> cached = get(artistId, cursorId, size);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. SingleFlight: putIfAbsent로 첫 번째 요청만 DB 조회
        String key = buildKey(artistId, cursorId, size);
        CompletableFuture<FeedListResult> myFuture = new CompletableFuture<>();
        CompletableFuture<FeedListResult> existing = inFlight.putIfAbsent(key, myFuture);

        if (existing != null) {
            // 다른 스레드가 진행 중 → 최대 5초 대기
            try {
                return existing.get(SINGLEFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[FeedCache] singleFlight wait failed key={}", key, e);
                return loader.get(); // fail-open: 직접 DB 조회
            }
        }

        // 3. 첫 번째 요청: DB 조회 → 캐시 저장 → future 공유
        try {
            FeedListResult result = loader.get();
            put(artistId, cursorId, size, result);
            myFuture.complete(result);
            return result;
        } catch (RuntimeException e) {
            myFuture.completeExceptionally(e);
            throw e;
        } catch (Error e) {
            // OOM 등 Error 발생 시에도 대기 스레드가 5초 full timeout 없이 즉시 fail-open 처리되도록
            myFuture.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, myFuture);
        }
    }

    @Override
    public void evictByArtistId(Long artistId) {
        String pattern = KEY_PREFIX + artistId + ":*";
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                List<byte[]> toDelete = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    cursor.forEachRemaining(toDelete::add);
                } catch (Exception e) {
                    log.warn("[FeedCache] scan error pattern={}", pattern, e);
                }
                if (!toDelete.isEmpty()) {
                    connection.del(toDelete.toArray(new byte[0][]));
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[FeedCache] evict failed artistId={}", artistId, e);
        }
    }

    private String buildKey(Long artistId, Long cursorId, int size) {
        return KEY_PREFIX + artistId + ":cursor:" + cursorId + ":size:" + size;
    }
}
