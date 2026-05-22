package com.fandrops.payment.api.queue;

import com.fandrops.payment.domain.queue.WaitQueueStatus;

public class QueueStatusResponse {

    private final long position;
    private final WaitQueueStatus status;
    private final long estimatedWaitSec;

    public QueueStatusResponse(long position, WaitQueueStatus status, long estimatedWaitSec) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
    }

    public long getPosition() { return position; }
    public WaitQueueStatus getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
}