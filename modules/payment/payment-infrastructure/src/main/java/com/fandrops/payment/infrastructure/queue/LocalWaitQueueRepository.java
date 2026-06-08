package com.fandrops.payment.infrastructure.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Redis 없는 로컬 환경용 인메모리 구현체.
 * productId별 LinkedHashMap(삽입 순서)으로 WAITING 순번을 보장한다.
 */
@Repository
@Profile("local")
public class LocalWaitQueueRepository implements WaitQueueRepository {

    // key: productId → (fanId → WaitQueueEntry)
    private final ConcurrentHashMap<Long, LinkedHashMap<Long, WaitQueueEntry>> store =
            new ConcurrentHashMap<>();

    @Override
    public synchronized WaitQueueEntry join(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue =
                store.computeIfAbsent(productId, k -> new LinkedHashMap<>());

        WaitQueueEntry existing = queue.get(fanId);
        if (existing != null && !existing.getStatus().isTerminal()) {
            long position = positionOf(fanId, queue);
            return new WaitQueueEntry(
                    existing.getQueueId(), fanId, productId,
                    existing.getStatus(), position, existing.getJoinedAt());
        }

        String queueId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        long position = waitingCount(queue) + 1L;
        WaitQueueEntry entry = new WaitQueueEntry(
                queueId, fanId, productId, WaitQueueStatus.WAITING, position, now);
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
        long position = stored.getStatus() == WaitQueueStatus.WAITING
                ? positionOf(fanId, queue) : 0L;
        return Optional.of(new WaitQueueEntry(
                stored.getQueueId(), fanId, productId,
                stored.getStatus(), position, stored.getJoinedAt(),
                stored.getProcessingStartAt()));
    }

    @Override
    public synchronized long getPosition(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return -1L;
        }
        return positionOf(fanId, queue);
    }

    @Override
    public synchronized void exit(Long fanId, Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return;
        }
        WaitQueueEntry entry = queue.get(fanId);
        if (entry != null && entry.getStatus() == WaitQueueStatus.WAITING) {
            queue.remove(fanId);
        }
    }

    @Override
    public synchronized boolean transitionToProcessing(Long fanId, Long productId,
                                                       Instant processingStartAt) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return false;
        }
        WaitQueueEntry entry = queue.get(fanId);
        if (entry == null || entry.getStatus() != WaitQueueStatus.WAITING) {
            return false;
        }
        queue.put(fanId, entry.withProcessing(processingStartAt));
        return true;
    }

    @Override
    public synchronized void transitionToTerminal(Long fanId, Long productId,
                                                  WaitQueueStatus terminal) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return;
        }
        WaitQueueEntry entry = queue.get(fanId);
        if (entry != null && entry.getStatus() == WaitQueueStatus.PROCESSING) {
            queue.put(fanId, entry.withTerminal(terminal));
        }
    }

    @Override
    public synchronized List<Long> findTopWaitingFanIds(Long productId, int limit) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return List.of();
        }
        return queue.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == WaitQueueStatus.WAITING)
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<Long> findProcessingExpiredFanIds(Long productId, Instant threshold) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return List.of();
        }
        return queue.entrySet().stream()
                .filter(e -> e.getValue().getStatus() == WaitQueueStatus.PROCESSING
                        && e.getValue().getProcessingStartAt() != null
                        && e.getValue().getProcessingStartAt().isBefore(threshold))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public synchronized long countProcessing(Long productId) {
        LinkedHashMap<Long, WaitQueueEntry> queue = store.get(productId);
        if (queue == null) {
            return 0L;
        }
        return queue.values().stream()
                .filter(e -> e.getStatus() == WaitQueueStatus.PROCESSING)
                .count();
    }

    // WAITING 항목만 순번 계산 (PROCESSING/Terminal 제외)
    private long positionOf(Long fanId, LinkedHashMap<Long, WaitQueueEntry> queue) {
        long pos = 1;
        for (Map.Entry<Long, WaitQueueEntry> e : queue.entrySet()) {
            if (e.getValue().getStatus() != WaitQueueStatus.WAITING) {
                continue;
            }
            if (e.getKey().equals(fanId)) {
                return pos;
            }
            pos++;
        }
        return -1L;
    }

    @Override
    public synchronized Set<Long> findActiveProductIds() {
        Set<Long> result = new HashSet<>();
        for (Map.Entry<Long, LinkedHashMap<Long, WaitQueueEntry>> entry : store.entrySet()) {
            boolean hasWaiting = entry.getValue().values().stream()
                    .anyMatch(e -> e.getStatus() == WaitQueueStatus.WAITING);
            if (hasWaiting) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private long waitingCount(LinkedHashMap<Long, WaitQueueEntry> queue) {
        return queue.values().stream()
                .filter(e -> e.getStatus() == WaitQueueStatus.WAITING)
                .count();
    }
}