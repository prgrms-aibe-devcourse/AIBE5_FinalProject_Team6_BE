package com.fandrops.payment.application.payment;

import java.time.Instant;

public class PaymentWebhookCommand {

    private final String tossPaymentKey;
    private final String tossStatus;
    private final String method;
    private final long amount;
    private final Long orderId;
    private final Instant approvedAt;

    public PaymentWebhookCommand(String tossPaymentKey, String tossStatus, String method,
                                  long amount, Long orderId, Instant approvedAt) {
        this.tossPaymentKey = tossPaymentKey;
        this.tossStatus = tossStatus;
        this.method = method;
        this.amount = amount;
        this.orderId = orderId;
        this.approvedAt = approvedAt;
    }

    public String getTossPaymentKey() { return tossPaymentKey; }
    public String getTossStatus() { return tossStatus; }
    public String getMethod() { return method; }
    public long getAmount() { return amount; }
    public Long getOrderId() { return orderId; }
    public Instant getApprovedAt() { return approvedAt; }
}