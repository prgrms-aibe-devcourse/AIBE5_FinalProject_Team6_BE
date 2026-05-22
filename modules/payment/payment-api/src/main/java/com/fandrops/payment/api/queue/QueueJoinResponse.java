package com.fandrops.payment.api.queue;

public class QueueJoinResponse {

    private final String queueId;
    private final long position;
    private final ApiQueueStatus status;

    public QueueJoinResponse(String queueId, long position, ApiQueueStatus status) {
        this.queueId = queueId;
        this.position = position;
        this.status = status;
    }

    public String getQueueId() { return queueId; }
    public long getPosition() { return position; }
    public ApiQueueStatus getStatus() { return status; }
}