package com.fandrops.payment.domain.queue;

import java.time.Instant;

public class WaitQueueEntry {

    private final String queueId;
    private final Long fanId;
    private final Long productId;
    private final WaitQueueStatus status;
    private final long position;
    private final Instant joinedAt;

    public WaitQueueEntry(String queueId, Long fanId, Long productId,
                          WaitQueueStatus status, long position, Instant joinedAt) {
        this.queueId = queueId;
        this.fanId = fanId;
        this.productId = productId;
        this.status = status;
        this.position = position;
        this.joinedAt = joinedAt;
    }

    public String getQueueId() { return queueId; }
    public Long getFanId() { return fanId; }
    public Long getProductId() { return productId; }
    public WaitQueueStatus getStatus() { return status; }
    public long getPosition() { return position; }
    public Instant getJoinedAt() { return joinedAt; }
}
