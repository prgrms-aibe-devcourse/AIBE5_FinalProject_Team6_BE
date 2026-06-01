package com.fandrops.payment.application.payment;

public class PaymentConfirmFailedException extends RuntimeException {

    private final String errorCode;

    public PaymentConfirmFailedException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}