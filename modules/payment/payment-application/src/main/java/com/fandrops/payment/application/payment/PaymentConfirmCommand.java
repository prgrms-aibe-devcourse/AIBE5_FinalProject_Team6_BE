package com.fandrops.payment.application.payment;

public class PaymentConfirmCommand {

    private final Long orderId;
    private final String tossPaymentKey;
    private final String orderPaymentKey;
    private final long amount;

    public PaymentConfirmCommand(Long orderId, String tossPaymentKey, String orderPaymentKey, long amount) {
        this.orderId = orderId;
        this.tossPaymentKey = tossPaymentKey;
        this.orderPaymentKey = orderPaymentKey;
        this.amount = amount;
    }

    public Long getOrderId() { return orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public String getOrderPaymentKey() { return orderPaymentKey; }
    public long getAmount() { return amount; }
}