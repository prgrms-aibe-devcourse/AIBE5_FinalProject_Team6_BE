package com.fandrops.payment.application.queue;

import com.fandrops.payment.domain.queue.WaitQueueEntry;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import com.fandrops.payment.domain.queue.WaitQueueStatus;

public class WaitQueueService {

    // 대기 1명당 예상 처리 시간(초). 추후 설정값으로 분리 가능.
    private static final long SECONDS_PER_POSITION = 3L;

    private final WaitQueueRepository waitQueueRepository;

    public WaitQueueService(WaitQueueRepository waitQueueRepository) {
        this.waitQueueRepository = waitQueueRepository;
    }

    public WaitQueueEntry join(QueueJoinCommand command) {
        return waitQueueRepository.join(command.getFanId(), command.getProductId());
    }

    public QueueStatusResult getStatus(Long fanId, Long productId) {
        WaitQueueEntry entry = waitQueueRepository.findEntry(fanId, productId)
                .orElseThrow(() -> new IllegalStateException("대기열에 등록되지 않은 팬입니다."));

        long position = waitQueueRepository.getPosition(fanId, productId);
        long estimatedWaitSec = position > 0 ? position * SECONDS_PER_POSITION : 0L;

        return new QueueStatusResult(position, entry.getStatus(), estimatedWaitSec);
    }
}