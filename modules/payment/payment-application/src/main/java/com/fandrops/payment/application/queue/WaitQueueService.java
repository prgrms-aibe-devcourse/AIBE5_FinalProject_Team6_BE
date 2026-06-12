package com.fandrops.payment.application.queue;

import com.fandrops.payment.domain.queue.AccessTicketRepository;
import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WaitQueueService {

    private static final long SECONDS_PER_POSITION = 3L;

    private final WaitQueueRepository waitQueueRepository;
    private final AccessTicketRepository accessTicketRepository;

    public WaitQueueService(WaitQueueRepository waitQueueRepository,
                            AccessTicketRepository accessTicketRepository) {
        this.waitQueueRepository = waitQueueRepository;
        this.accessTicketRepository = accessTicketRepository;
    }

    public QueueJoinResult join(QueueJoinCommand command) {
        WaitQueueEntry entry = waitQueueRepository.join(command.getFanId(), command.getProductId());
        return new QueueJoinResult(entry.getQueueId(), entry.getPosition(), entry.getStatus().name());
    }

    public QueueStatusResult getStatus(Long fanId, Long productId) {
        WaitQueueEntry entry = waitQueueRepository.findEntry(fanId, productId)
                .orElseThrow(() -> new IllegalStateException("대기열에 등록되지 않은 팬입니다."));

        long position = entry.getPosition();
        long estimatedWaitSec = position > 0 ? position * SECONDS_PER_POSITION : 0L;

        return new QueueStatusResult(position, entry.getStatus().name(), estimatedWaitSec);
    }

    /** SSE 연결과 무관하게 WAITING 항목이 존재하는 productId 집합 반환. */
    public Set<Long> getActiveProductIds() {
        return waitQueueRepository.findActiveProductIds();
    }

    /** 대기열 이탈 (WAITING 상태에서만 유효). */
    public void exit(Long fanId, Long productId) {
        waitQueueRepository.exit(fanId, productId);
    }

    /**
     * 대기열 상위 maxCount명을 WAITING → PROCESSING으로 전이하고 Access Ticket을 발급한다.
     * 현재 PROCESSING 수가 maxConcurrent 이상이면 진입을 중단한다.
     */
    public List<QueueAdvanceResult> advanceQueue(Long productId, int maxCount, long maxConcurrent) {
        long currentProcessing = waitQueueRepository.countProcessing(productId);
        int available = (int) Math.max(0, maxConcurrent - currentProcessing);
        if (available == 0) {
            return List.of();
        }

        List<Long> candidates = waitQueueRepository.findTopWaitingFanIds(productId, Math.min(maxCount, available));
        List<QueueAdvanceResult> results = new ArrayList<>();

        for (Long fanId : candidates) {
            boolean advanced = waitQueueRepository.transitionToProcessing(fanId, productId, Instant.now());
            if (advanced) {
                String token = accessTicketRepository.issue(fanId, productId);
                results.add(new QueueAdvanceResult(fanId, productId, token));
            }
        }
        return results;
    }

    /**
     * threshold 이전에 PROCESSING 진입한 entry를 EXPIRED로 전이하고 토큰을 무효화한다.
     * 반환값: 만료 처리된 fanId 목록.
     */
    public List<Long> expireTimeouts(Long productId, Instant threshold) {
        List<Long> expired = waitQueueRepository.findProcessingExpiredFanIds(productId, threshold);
        for (Long fanId : expired) {
            waitQueueRepository.transitionToTerminal(fanId, productId, WaitQueueStatus.EXPIRED);
            accessTicketRepository.invalidate(fanId, productId);
        }
        return expired;
    }
}