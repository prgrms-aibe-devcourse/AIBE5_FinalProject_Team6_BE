package com.fandrops.payment.application.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;

public class WaitQueueService {

    // 대기 1명당 예상 처리 시간(초). 추후 설정값으로 분리 가능.
    private static final long SECONDS_PER_POSITION = 3L;

    private final WaitQueueRepository waitQueueRepository;

    public WaitQueueService(WaitQueueRepository waitQueueRepository) {
        this.waitQueueRepository = waitQueueRepository;
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
}