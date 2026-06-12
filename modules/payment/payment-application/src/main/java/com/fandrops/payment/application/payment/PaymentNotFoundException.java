package com.fandrops.payment.application.payment;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long orderId) {
        super("결제 정보를 찾을 수 없습니다: orderId=" + orderId);
    }
}