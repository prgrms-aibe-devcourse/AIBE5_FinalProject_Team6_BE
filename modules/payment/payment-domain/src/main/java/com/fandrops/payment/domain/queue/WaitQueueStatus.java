package com.fandrops.payment.domain.queue;

public enum WaitQueueStatus {
    WAITING,
    PROCESSING,
    DONE,
    EXPIRED;

    public boolean isTerminal() {
        return this == DONE || this == EXPIRED;
    }
}
