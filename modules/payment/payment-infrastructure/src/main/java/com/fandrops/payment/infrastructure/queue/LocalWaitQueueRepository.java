package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Redis 없는 로컬 환경용 인메모리 구현체.
 * 순서 보장을 위해 productId별 LinkedHashMap(삽입 순서)을 사용한다.
 */
@Repository
@Profile("local")
public class LocalWaitQueueRepository implements WaitQueueRepository {

    // key: productId → (fanId → WaitQueueEntry)
    private final ConcurrentHashMap<Long, LinkedHashMap<Long, WaitQueueEntry>> store =
            new ConcurrentHashMap<>();

    @Override
    public synchronized WaitQueueEntry join(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.computeIfAbsent(productId, k -> new LinkedHashMap<>());

        WaitQueueEntry existing = queue.get(fanId);
        if (existing != null && !existing.getStatus().isTerminal()) {
            long position = positionOf(fanId, queue);
            return new WaitQueueEntry(existing.getQueueId(), fanId, productId,
                    existing.getStatus(), position, existing.getJoinedAt());
        }

        String queueId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        long position = queue.size() + 1L;
        WaitQueueEntry entry = new WaitQueueEntry(queueId, fanId, productId, WaitQueueStatus.WAITING, position, now);
        queue.put(fanId, entry);
        return entry;
    }

    @Override
    public synchronized Optional<WaitQueueEntry> findEntry(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return Optional.empty();
        }

        WaitQueueEntry stored = queue.get(fanId);
        if (stored == null) {
            return Optional.empty();
        }

        long position = positionOf(fanId, queue);
        return Optional.of(new WaitQueueEntry(stored.getQueueId(), fanId, productId,
                stored.getStatus(), position, stored.getJoinedAt()));
    }

    @Override
    public synchronized long getPosition(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return -1L;
        }
        return positionOf(fanId, queue);
    }

    private long positionOf(Long fanId, LinkedHashMap<Long, WaitQueueEntry> queue) {
        long pos = 1;
        for (Long id : queue.keySet()) {
            if (id.equals(fanId)) {
                return pos;
            }
            pos++;
        }
        return -1L;
    }
}
