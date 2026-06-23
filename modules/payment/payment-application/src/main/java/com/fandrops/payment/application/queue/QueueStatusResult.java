package com.fandrops.payment.application.queue;

public class QueueStatusResult {

    private final long position;
    private final String status;
    private final long estimatedWaitSec;
    private final String token;

    public QueueStatusResult(long position, String status, long estimatedWaitSec) {
        this(position, status, estimatedWaitSec, null);
    }

    public QueueStatusResult(long position, String status, long estimatedWaitSec, String token) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
        this.token = token;
    }

    public long getPosition() { return position; }
    public String getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
    public String getToken() { return token; }
}