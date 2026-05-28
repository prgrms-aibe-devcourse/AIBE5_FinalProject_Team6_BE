package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payment")
class PaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_key", unique = true)
    private String paymentKey;

    @Column(nullable = false)
    private long amount;

    @Column(name = "method")
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected PaymentJpaEntity() {}

    static PaymentJpaEntity fromDomain(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.id = payment.getId();
        entity.orderId = payment.getOrderId();
        entity.paymentKey = payment.getTossPaymentKey();
        entity.amount = payment.getAmount();
        entity.method = payment.getPaymentMethod();
        entity.status = payment.getStatus();
        entity.paidAt = payment.getPaidAt();
        entity.failedAt = payment.getFailedAt();
        return entity;
    }

    Payment toDomain() {
        return Payment.reconstitute(id, orderId, paymentKey, amount, method, status, paidAt, failedAt);
    }
}