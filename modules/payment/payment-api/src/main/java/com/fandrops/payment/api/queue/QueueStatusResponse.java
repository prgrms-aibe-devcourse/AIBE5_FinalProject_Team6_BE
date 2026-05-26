package com.fandrops.payment.api.queue;

public class QueueStatusResponse {

    private final long position;
    private final ApiQueueStatus status;
    private final long estimatedWaitSec;

    public QueueStatusResponse(long position, ApiQueueStatus status, long estimatedWaitSec) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
    }

    public long getPosition() { return position; }
    public ApiQueueStatus getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
}
