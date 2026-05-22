package com.fandrops.payment.application.queue;

public class QueueJoinResult {

    private final String queueId;
    private final long position;
    private final String status;

    public QueueJoinResult(String queueId, long position, String status) {
        this.queueId = queueId;
        this.position = position;
        this.status = status;
    }

    public String getQueueId() { return queueId; }
    public long getPosition() { return position; }
    public String getStatus() { return status; }
}