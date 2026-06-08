package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local")
public class RedisWaitQueueRepository implements WaitQueueRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisWaitQueueRepository.class);

    // WAITING 팬 정렬 셋 (score = joinedAt ms)
    private static final String WAITING_KEY    = "queue:%d:waiting";
    // PROCESSING 팬 정렬 셋 (score = processingStartAt ms)
    private static final String PROCESSING_KEY = "queue:%d:processing";
    // 팬별 상태 해시
    private static final String FAN_KEY        = "queue:%d:fan:%d";

    private static final String FIELD_QUEUE_ID         = "queueId";
    private static final String FIELD_STATUS           = "status";
    private static final String FIELD_JOINED_AT        = "joinedAt";
    private static final String FIELD_PROCESSING_START = "processingStartAt";

    // Terminal이 아닌 entry 존재 시 0, 신규 등록 시 1
    private static final RedisScript<Long> JOIN_SCRIPT = RedisScript.of(
            "local s = redis.call('HGET', KEYS[2], 'status') " +
            "if s ~= false and s ~= 'DONE' and s ~= 'EXPIRED' then return 0 end " +
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
            "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]) " +
            "redis.call('HMSET', KEYS[2], 'queueId', ARGV[3], 'status', 'WAITING', 'joinedAt', ARGV[4]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[5]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[5]) " +
            "return 1",
            Long.class);

    // WAITING이면 waiting 셋·해시 삭제, 성공 시 1
    private static final RedisScript<Long> EXIT_SCRIPT = RedisScript.of(
            "local s = redis.call('HGET', KEYS[2], 'status') " +
            "if s ~= 'WAITING' then return 0 end " +
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
            "redis.call('DEL', KEYS[2]) " +
            "return 1",
            Long.class);

    // WAITING → PROCESSING 원자적 전이, 성공 시 1
    private static final RedisScript<Long> ADVANCE_SCRIPT = RedisScript.of(
            "local s = redis.call('HGET', KEYS[3], 'status') " +
            "if s ~= 'WAITING' then return 0 end " +
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
            "redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1]) " +
            "redis.call('HMSET', KEYS[3], 'status', 'PROCESSING', 'processingStartAt', ARGV[2]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[3]) " +
            "redis.call('EXPIRE', KEYS[3], ARGV[3]) " +
            "return 1",
            Long.class);

    // PROCESSING → DONE/EXPIRED 전이, 성공 시 1
    private static final RedisScript<Long> TERMINAL_SCRIPT = RedisScript.of(
            "local s = redis.call('HGET', KEYS[2], 'status') " +
            "if s ~= 'PROCESSING' then return 0 end " +
            "redis.call('ZREM', KEYS[1], ARGV[1]) " +
            "redis.call('HSET', KEYS[2], 'status', ARGV[2]) " +
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
                List.of(waitingKey(productId), fanKey(fanId, productId)),
                String.valueOf(fanId),
                String.valueOf(now.toEpochMilli()),
                queueId,
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
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(fanKey(fanId, productId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }

        String statusStr = (String) fields.get(FIELD_STATUS);
        if (statusStr == null) {
            return Optional.empty();
        }

        WaitQueueStatus status = WaitQueueStatus.valueOf(statusStr);
        String queueId = (String) fields.get(FIELD_QUEUE_ID);
        Instant joinedAt = Instant.ofEpochMilli(Long.parseLong((String) fields.get(FIELD_JOINED_AT)));

        String procStartStr = (String) fields.get(FIELD_PROCESSING_START);
        Instant processingStartAt = procStartStr != null
                ? Instant.ofEpochMilli(Long.parseLong(procStartStr)) : null;

        long position = status == WaitQueueStatus.WAITING ? getPosition(fanId, productId) : 0L;

        return Optional.of(new WaitQueueEntry(
                queueId, fanId, productId, status, position, joinedAt, processingStartAt));
    }

    @Override
    public long getPosition(Long fanId, Long productId) {
        Long rank = redisTemplate.opsForZSet().rank(waitingKey(productId), String.valueOf(fanId));
        return rank != null ? rank + 1 : -1L;
    }

    @Override
    public void exit(Long fanId, Long productId) {
        redisTemplate.execute(
                EXIT_SCRIPT,
                List.of(waitingKey(productId), fanKey(fanId, productId)),
                String.valueOf(fanId));
    }

    @Override
    public boolean transitionToProcessing(Long fanId, Long productId, Instant processingStartAt) {
        Long result = redisTemplate.execute(
                ADVANCE_SCRIPT,
                List.of(waitingKey(productId), processingKey(productId), fanKey(fanId, productId)),
                String.valueOf(fanId),
                String.valueOf(processingStartAt.toEpochMilli()),
                String.valueOf(ttlSeconds));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public void transitionToTerminal(Long fanId, Long productId, WaitQueueStatus terminal) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("terminal 상태가 아닙니다: " + terminal);
        }
        redisTemplate.execute(
                TERMINAL_SCRIPT,
                List.of(processingKey(productId), fanKey(fanId, productId)),
                String.valueOf(fanId),
                terminal.name());
    }

    @Override
    public List<Long> findTopWaitingFanIds(Long productId, int limit) {
        Set<String> members = redisTemplate.opsForZSet().range(waitingKey(productId), 0, limit - 1);
        if (members == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(members.size());
        for (String m : members) {
            result.add(Long.parseLong(m));
        }
        return result;
    }

    @Override
    public List<Long> findProcessingExpiredFanIds(Long productId, Instant threshold) {
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByScore(processingKey(productId), 0, threshold.toEpochMilli());
        if (members == null) {
            return List.of();
        }
        List<Long> result = new ArrayList<>(members.size());
        for (String m : members) {
            result.add(Long.parseLong(m));
        }
        return result;
    }

    @Override
    public long countProcessing(Long productId) {
        Long count = redisTemplate.opsForZSet().zCard(processingKey(productId));
        return count != null ? count : 0L;
    }

    @Override
    public Set<Long> findActiveProductIds() {
        Set<Long> productIds = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions().match("queue:*:waiting").count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Long size = redisTemplate.opsForZSet().zCard(key);
                if (size != null && size > 0) {
                    String[] parts = key.split(":");
                    if (parts.length >= 2) {
                        productIds.add(Long.parseLong(parts[1]));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WaitQueue] findActiveProductIds scan 실패", e);
        }
        return productIds;
    }

    private String waitingKey(Long productId) {
        return String.format(WAITING_KEY, productId);
    }

    private String processingKey(Long productId) {
        return String.format(PROCESSING_KEY, productId);
    }

    private String fanKey(Long fanId, Long productId) {
        return String.format(FAN_KEY, productId, fanId);
    }
}