package com.fandrops.order.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.port.ProductCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class ProductCacheAdapter implements ProductCachePort {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheAdapter.class);
    private static final String KEY_PREFIX = "order:products:";
    private static final Duration BASE_TTL = Duration.ofSeconds(120);
    private static final int JITTER_MAX_SECONDS = 30;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ProductListResponse> get(String type, Long artistId, Long cursor, int size) {
        String key = buildKey(type, artistId, cursor, size);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ProductListResponse.class));
        } catch (Exception e) {
            log.warn("[ProductCache] get failed key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(String type, Long artistId, Long cursor, int size, ProductListResponse result) {
        String key = buildKey(type, artistId, cursor, size);
        try {
            String json = objectMapper.writeValueAsString(result);
            int jitter = ThreadLocalRandom.current().nextInt(JITTER_MAX_SECONDS + 1);
            redisTemplate.opsForValue().set(key, json, BASE_TTL.plusSeconds(jitter));
        } catch (Exception e) {
            log.warn("[ProductCache] put failed key={}", key, e);
        }
    }

    @Override
    public void evictAll() {
        String pattern = KEY_PREFIX + "*";
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
                List<byte[]> toDelete = new ArrayList<>();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    cursor.forEachRemaining(toDelete::add);
                } catch (Exception e) {
                    log.warn("[ProductCache] scan error pattern={}", pattern, e);
                }
                if (!toDelete.isEmpty()) {
                    connection.del(toDelete.toArray(new byte[0][]));
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[ProductCache] evictAll failed", e);
        }
    }

    private String buildKey(String type, Long artistId, Long cursor, int size) {
        return KEY_PREFIX + type + ":" + artistId + ":cursor:" + cursor + ":size:" + size;
    }
}
