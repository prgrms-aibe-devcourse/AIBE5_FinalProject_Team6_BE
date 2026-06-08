package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import java.time.Instant;

public class PaymentDetailResult {

    private final Long id;
    private final Long orderId;
    private final long amount;
    private final String paymentMethod;
    private final String status;
    private final Instant paidAt;
    private final Instant failedAt;
    private final Instant createdAt;

    private PaymentDetailResult(Long id, Long orderId, long amount, String paymentMethod,
                                String status, Instant paidAt, Instant failedAt, Instant createdAt) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.createdAt = createdAt;
    }

    public static PaymentDetailResult from(Payment payment) {
        return new PaymentDetailResult(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus().name(),
                payment.getPaidAt(),
                payment.getFailedAt(),
                payment.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCreatedAt() { return createdAt; }
}