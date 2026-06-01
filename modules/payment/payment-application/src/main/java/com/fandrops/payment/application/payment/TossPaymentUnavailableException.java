package com.fandrops.payment.application.payment;

public class TossPaymentUnavailableException extends RuntimeException {

    public TossPaymentUnavailableException(String message) {
        super(message);
    }
}