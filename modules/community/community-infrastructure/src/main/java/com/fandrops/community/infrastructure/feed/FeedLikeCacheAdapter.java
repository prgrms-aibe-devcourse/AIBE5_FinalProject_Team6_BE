package com.fandrops.community.infrastructure.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.community.application.port.FeedLikeCachePort;
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
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class FeedLikeCacheAdapter implements FeedLikeCachePort {

    private static final Logger log = LoggerFactory.getLogger(FeedLikeCacheAdapter.class);
    private static final String KEY_PREFIX = "feed:liked:";
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final TypeReference<Set<Long>> SET_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public FeedLikeCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 캐시 키: feed:liked:{fanId}:{sortedFeedIds}
    // FeedListResult와 동일 페이지 feedIds → 키 안정성 보장 (60-90s FeedCache TTL 내 동일 feedIds)
    @Override
    public Set<Long> getOrLoad(Long fanId, List<Long> feedIds, Supplier<Set<Long>> loader) {
        if (feedIds.isEmpty()) {
            return Set.of();
        }
        String key = buildKey(fanId, feedIds);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, SET_TYPE);
            }
        } catch (Exception e) {
            log.warn("[FeedLikeCache] get failed key={}", key, e);
        }
        Set<Long> result = loader.get();
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), TTL);
        } catch (Exception e) {
            log.warn("[FeedLikeCache] put failed key={}", key, e);
        }
        return result;
    }

    @Override
    public void evictByFanId(Long fanId) {
        String pattern = KEY_PREFIX + fanId + ":*";
        try {
            redisTemplate.execute((RedisCallback<Void>) conn -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                List<byte[]> keys = new ArrayList<>();
                try (Cursor<byte[]> cursor = conn.scan(options)) {
                    cursor.forEachRemaining(keys::add);
                } catch (Exception e) {
                    log.warn("[FeedLikeCache] scan failed pattern={}", pattern, e);
                }
                if (!keys.isEmpty()) {
                    conn.del(keys.toArray(new byte[0][]));
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[FeedLikeCache] evict failed fanId={}", fanId, e);
        }
    }

    private String buildKey(Long fanId, List<Long> feedIds) {
        String ids = feedIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return KEY_PREFIX + fanId + ":" + ids;
    }
}