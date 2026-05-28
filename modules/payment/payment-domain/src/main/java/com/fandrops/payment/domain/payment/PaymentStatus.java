package com.fandrops.payment.domain.payment;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }
}