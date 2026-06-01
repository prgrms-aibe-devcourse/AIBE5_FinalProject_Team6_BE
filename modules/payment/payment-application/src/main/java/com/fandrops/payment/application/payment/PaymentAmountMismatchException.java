package com.fandrops.payment.application.payment;

public class PaymentAmountMismatchException extends RuntimeException {

    public PaymentAmountMismatchException(Long orderId, long expected, long actual) {
        super(String.format("결제 금액 불일치: orderId=%d, expected=%d, actual=%d", orderId, expected, actual));
    }
}