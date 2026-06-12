package com.fandrops.payment.application.payment;

public class PaymentLockConflictException extends RuntimeException {

    public PaymentLockConflictException(Long orderId) {
        super("결제 동시 처리 충돌: orderId=" + orderId);
    }
}