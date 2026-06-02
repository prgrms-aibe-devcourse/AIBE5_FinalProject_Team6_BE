package com.fandrops.payment.domain.payment;

import java.time.Instant;

public class Payment {

    private final Long id;
    private final Long orderId;
    private String tossPaymentKey;
    private final long amount;
    private String paymentMethod;
    private PaymentStatus status;
    private Instant paidAt;
    private Instant failedAt;
    private final Instant createdAt;
    private final int version;

    private Payment(Long id, Long orderId, String tossPaymentKey,
                    long amount, String paymentMethod, PaymentStatus status,
                    Instant paidAt, Instant failedAt, Instant createdAt, int version) {
        this.id = id;
        this.orderId = orderId;
        this.tossPaymentKey = tossPaymentKey;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.paidAt = paidAt;
        this.failedAt = failedAt;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static Payment create(Long orderId, long amount) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId는 null일 수 없습니다");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 합니다");
        }
        return new Payment(null, orderId, null, amount, null, PaymentStatus.PENDING,
                null, null, Instant.now(), 0);
    }

    public static Payment reconstitute(Long id, Long orderId, String tossPaymentKey,
                                       long amount, String paymentMethod, PaymentStatus status,
                                       Instant paidAt, Instant failedAt, Instant createdAt,
                                       int version) {
        return new Payment(id, orderId, tossPaymentKey, amount, paymentMethod,
                status, paidAt, failedAt, createdAt, version);
    }

    // PENDING → SUCCESS (P-2: paidAt NOT NULL)
    public void confirm(String tossPaymentKey, String paymentMethod, Instant paidAt) {
        if (status.isTerminal()) {
            throw new IllegalStateException("이미 종료된 결제 상태입니다: " + status);
        }
        if (tossPaymentKey == null || tossPaymentKey.isBlank()) {
            throw new IllegalArgumentException("tossPaymentKey는 null이거나 빈 값일 수 없습니다");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("paymentMethod는 null이거나 빈 값일 수 없습니다");
        }
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt은 null일 수 없습니다");
        }
        this.tossPaymentKey = tossPaymentKey;
        this.paymentMethod = paymentMethod;
        this.status = PaymentStatus.SUCCESS;
        this.paidAt = paidAt;
    }

    // PENDING → FAILED (P-3: failedAt NOT NULL)
    public void fail(Instant failedAt) {
        if (status.isTerminal()) {
            throw new IllegalStateException("이미 종료된 결제 상태입니다: " + status);
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt은 null일 수 없습니다");
        }
        this.status = PaymentStatus.FAILED;
        this.failedAt = failedAt;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public long getAmount() { return amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getVersion() { return version; }
}