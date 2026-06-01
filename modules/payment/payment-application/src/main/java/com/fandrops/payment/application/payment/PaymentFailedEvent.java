package com.fandrops.payment.application.payment;

public class PaymentFailedEvent {

    private final Long orderId;

    public PaymentFailedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
}