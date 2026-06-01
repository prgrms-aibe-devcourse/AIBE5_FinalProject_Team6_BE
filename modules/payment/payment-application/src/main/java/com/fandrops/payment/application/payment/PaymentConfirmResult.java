package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;

public class PaymentConfirmResult {

    private final Long orderId;
    private final String tossPaymentKey;
    private final PaymentStatus status;
    private final Instant paidAt;

    private PaymentConfirmResult(Long orderId, String tossPaymentKey,
                                 PaymentStatus status, Instant paidAt) {
        this.orderId = orderId;
        this.tossPaymentKey = tossPaymentKey;
        this.status = status;
        this.paidAt = paidAt;
    }

    public static PaymentConfirmResult from(Payment payment) {
        return new PaymentConfirmResult(
                payment.getOrderId(),
                payment.getTossPaymentKey(),
                payment.getStatus(),
                payment.getPaidAt()
        );
    }

    public Long getOrderId() { return orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public PaymentStatus getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
}