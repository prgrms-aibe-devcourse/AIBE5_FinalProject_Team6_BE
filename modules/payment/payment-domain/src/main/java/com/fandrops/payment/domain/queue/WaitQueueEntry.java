package com.fandrops.payment.domain.queue;

import java.time.Instant;

public class WaitQueueEntry {

    private final String queueId;
    private final Long fanId;
    private final Long productId;
    private final WaitQueueStatus status;
    private final long position;
    private final Instant joinedAt;
    private final Instant processingStartAt; // null unless PROCESSING

    public WaitQueueEntry(String queueId, Long fanId, Long productId,
                          WaitQueueStatus status, long position, Instant joinedAt) {
        this(queueId, fanId, productId, status, position, joinedAt, null);
    }

    public WaitQueueEntry(String queueId, Long fanId, Long productId,
                          WaitQueueStatus status, long position, Instant joinedAt,
                          Instant processingStartAt) {
        this.queueId = queueId;
        this.fanId = fanId;
        this.productId = productId;
        this.status = status;
        this.position = position;
        this.joinedAt = joinedAt;
        this.processingStartAt = processingStartAt;
    }

    public WaitQueueEntry withProcessing(Instant now) {
        return new WaitQueueEntry(queueId, fanId, productId, WaitQueueStatus.PROCESSING, 0L, joinedAt, now);
    }

    public WaitQueueEntry withTerminal(WaitQueueStatus terminal) {
        return new WaitQueueEntry(queueId, fanId, productId, terminal, 0L, joinedAt, processingStartAt);
    }

    public String getQueueId() { return queueId; }
    public Long getFanId() { return fanId; }
    public Long getProductId() { return productId; }
    public WaitQueueStatus getStatus() { return status; }
    public long getPosition() { return position; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getProcessingStartAt() { return processingStartAt; }
}
