package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local")
public class RedisWaitQueueRepository implements WaitQueueRepository {

    private static final String ENTRIES_KEY = "queue:%d:entries";
    private static final String FAN_KEY = "queue:%d:fan:%d";
    private static final String FIELD_QUEUE_ID = "queueId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_JOINED_AT = "joinedAt";

    // 비터미널 entry 존재 시 0, 신규 등록(ZREM→ZADD→HMSET→EXPIRE) 시 1 반환
    private static final RedisScript<Long> JOIN_SCRIPT = RedisScript.of(
            "local s = redis.call('HGET', KEYS[2], 'status') " +
            "if s ~= false and s ~= 'DONE' and s ~= 'EXPIRED' then return 0 end " +
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]) " +
            "redis.call('HMSET', KEYS[2], 'queueId', ARGV[3], 'status', ARGV[4], 'joinedAt', ARGV[5]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[6]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[6]) " +
            "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public RedisWaitQueueRepository(
            StringRedisTemplate redisTemplate,
            @Value("${fandrops.payment.queue.ttl-seconds:86400}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public WaitQueueEntry join(Long fanId, Long productId) {
        String queueId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Long created = redisTemplate.execute(
                JOIN_SCRIPT,
                List.of(entriesKey(productId), fanKey(fanId, productId)),
                String.valueOf(fanId),
                String.valueOf(now.toEpochMilli()),
                queueId,
                WaitQueueStatus.WAITING.name(),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(ttlSeconds));

        if (created == null || created == 0L) {
            return findEntry(fanId, productId)
                    .orElseThrow(() -> new IllegalStateException("대기열 entry 조회 실패"));
        }

        long position = getPosition(fanId, productId);
        return new WaitQueueEntry(queueId, fanId, productId, WaitQueueStatus.WAITING, position, now);
    }

    @Override
    public Optional<WaitQueueEntry> findEntry(Long fanId, Long productId) {
        String fanKey = fanKey(fanId, productId);
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(fanKey);
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        String statusStr = (String) fields.get(FIELD_STATUS);
        if (statusStr == null) {
            return Optional.empty();
        }

        String queueId = (String) fields.get(FIELD_QUEUE_ID);
        String joinedAtStr = (String) fields.get(FIELD_JOINED_AT);

        WaitQueueStatus status = WaitQueueStatus.valueOf(statusStr);
        Instant joinedAt = Instant.ofEpochMilli(Long.parseLong(joinedAtStr));
        long position = getPosition(fanId, productId);

        return Optional.of(new WaitQueueEntry(queueId, fanId, productId, status, position, joinedAt));
    }

    @Override
    public long getPosition(Long fanId, Long productId) {
        Long rank = redisTemplate.opsForZSet().rank(entriesKey(productId), String.valueOf(fanId));
        return rank != null ? rank + 1 : -1L;
    }

    private String entriesKey(Long productId) {
        return String.format(ENTRIES_KEY, productId);
    }

    private String fanKey(Long fanId, Long productId) {
        return String.format(FAN_KEY, productId, fanId);
    }
}