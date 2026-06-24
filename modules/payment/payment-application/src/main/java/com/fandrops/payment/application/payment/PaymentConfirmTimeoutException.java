package com.fandrops.payment.application.payment;

public class PaymentConfirmTimeoutException extends RuntimeException {
    public PaymentConfirmTimeoutException(String message) {
        super(message);
    }
}
