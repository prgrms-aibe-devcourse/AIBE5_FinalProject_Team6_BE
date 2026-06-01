package com.fandrops.payment.application.payment;

public class PaymentConfirmCommand {

    private final Long orderId;
    private final String tossPaymentKey;
    private final long amount;

    public PaymentConfirmCommand(Long orderId, String tossPaymentKey, long amount) {
        this.orderId = orderId;
        this.tossPaymentKey = tossPaymentKey;
        this.amount = amount;
    }

    public Long getOrderId() { return orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public long getAmount() { return amount; }
}