package com.fandrops.payment.api.queue;

import com.fandrops.payment.domain.queue.WaitQueueStatus;

public class QueueJoinResponse {

    private final String queueId;
    private final long position;
    private final WaitQueueStatus status;

    public QueueJoinResponse(String queueId, long position, WaitQueueStatus status) {
        this.queueId = queueId;
        this.position = position;
        this.status = status;
    }

    public String getQueueId() { return queueId; }
    public long getPosition() { return position; }
    public WaitQueueStatus getStatus() { return status; }
}