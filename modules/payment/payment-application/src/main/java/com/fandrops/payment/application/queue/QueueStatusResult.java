package com.fandrops.payment.application.queue;

public class QueueStatusResult {

    private final long position;
    private final String status;
    private final long estimatedWaitSec;

    public QueueStatusResult(long position, String status, long estimatedWaitSec) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
    }

    public long getPosition() { return position; }
    public String getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
}