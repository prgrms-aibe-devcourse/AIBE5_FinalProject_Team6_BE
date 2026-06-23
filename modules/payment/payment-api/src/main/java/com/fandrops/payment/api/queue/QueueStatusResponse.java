package com.fandrops.payment.api.queue;

public class QueueStatusResponse {

    private final long position;
    private final ApiQueueStatus status;
    private final long estimatedWaitSec;
    private final String token;

    public QueueStatusResponse(long position, ApiQueueStatus status, long estimatedWaitSec) {
        this(position, status, estimatedWaitSec, null);
    }

    public QueueStatusResponse(long position, ApiQueueStatus status, long estimatedWaitSec, String token) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
        this.token = token;
    }

    public long getPosition() { return position; }
    public ApiQueueStatus getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
    public String getToken() { return token; }
}
