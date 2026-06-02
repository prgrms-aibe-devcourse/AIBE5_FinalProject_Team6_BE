package com.fandrops.payment.application.payment;

public class PaymentApprovedEvent {

    private final Long orderId;

    public PaymentApprovedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() { return orderId; }
}
