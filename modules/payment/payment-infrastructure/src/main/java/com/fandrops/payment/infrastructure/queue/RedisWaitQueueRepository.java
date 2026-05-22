package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisWaitQueueRepository implements WaitQueueRepository {

    private static final String ENTRIES_KEY = "queue:%d:entries";
    private static final String FAN_KEY = "queue:%d:fan:%d";
    private static final String FIELD_QUEUE_ID = "queueId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_JOINED_AT = "joinedAt";

    private final StringRedisTemplate redisTemplate;

    public RedisWaitQueueRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public WaitQueueEntry join(Long fanId, Long productId) {
        String fanKey = fanKey(fanId, productId);

        // 이미 활성 entry 존재 시 기존 entry 반환 (W-1: Terminal 재활성화 금지)
        Optional<WaitQueueEntry> existing = findEntry(fanId, productId);
        if (existing.isPresent() && !existing.get().getStatus().isTerminal()) {
            return existing.get();
        }

        String queueId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        double score = now.toEpochMilli();

        // Sorted Set에 등록 (NX: 이미 있으면 스코어 유지)
        redisTemplate.opsForZSet().addIfAbsent(entriesKey(productId), String.valueOf(fanId), score);

        // 팬 상태 Hash 저장
        redisTemplate.opsForHash().put(fanKey, FIELD_QUEUE_ID, queueId);
        redisTemplate.opsForHash().put(fanKey, FIELD_STATUS, WaitQueueStatus.WAITING.name());
        redisTemplate.opsForHash().put(fanKey, FIELD_JOINED_AT, String.valueOf(now.toEpochMilli()));

        long position = getPosition(fanId, productId);
        return new WaitQueueEntry(queueId, fanId, productId, WaitQueueStatus.WAITING, position, now);
    }

    @Override
    public Optional<WaitQueueEntry> findEntry(Long fanId, Long productId) {
        String fanKey = fanKey(fanId, productId);
        String statusStr = (String) redisTemplate.opsForHash().get(fanKey, FIELD_STATUS);
        if (statusStr == null) {
            return Optional.empty();
        }

        String queueId = (String) redisTemplate.opsForHash().get(fanKey, FIELD_QUEUE_ID);
        String joinedAtStr = (String) redisTemplate.opsForHash().get(fanKey, FIELD_JOINED_AT);
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