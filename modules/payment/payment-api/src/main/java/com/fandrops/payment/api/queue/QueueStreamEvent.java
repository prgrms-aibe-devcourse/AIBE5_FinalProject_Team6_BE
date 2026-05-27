package com.fandrops.payment.api.queue;

public class QueueStreamEvent {

    private final long position;
    private final String status;
    private final long estimatedWaitSec;
    private final String accessToken; // PROCESSING 전이 시에만 non-null

    private QueueStreamEvent(long position, String status, long estimatedWaitSec, String accessToken) {
        this.position = position;
        this.status = status;
        this.estimatedWaitSec = estimatedWaitSec;
        this.accessToken = accessToken;
    }

    public static QueueStreamEvent waiting(long position, long estimatedWaitSec) {
        return new QueueStreamEvent(position, "WAITING", estimatedWaitSec, null);
    }

    public static QueueStreamEvent processing(String accessToken) {
        return new QueueStreamEvent(0, "PROCESSING", 0, accessToken);
    }

    public static QueueStreamEvent expired() {
        return new QueueStreamEvent(0, "EXPIRED", 0, null);
    }

    public long getPosition() { return position; }
    public String getStatus() { return status; }
    public long getEstimatedWaitSec() { return estimatedWaitSec; }
    public String getAccessToken() { return accessToken; }
}