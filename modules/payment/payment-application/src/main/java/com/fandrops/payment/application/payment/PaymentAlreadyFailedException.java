package com.fandrops.payment.application.payment;

public class PaymentAlreadyFailedException extends RuntimeException {

    public PaymentAlreadyFailedException(Long orderId) {
        super("이미 실패한 결제입니다: orderId=" + orderId);
    }
}