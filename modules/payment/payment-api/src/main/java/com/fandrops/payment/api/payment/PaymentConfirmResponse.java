package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentConfirmResult;
import java.time.Instant;

public class PaymentConfirmResponse {

    private final Long orderId;
    private final String tossPaymentKey;
    private final String status;
    private final Instant paidAt;

    private PaymentConfirmResponse(Long orderId, String tossPaymentKey, String status, Instant paidAt) {
        this.orderId = orderId;
        this.tossPaymentKey = tossPaymentKey;
        this.status = status;
        this.paidAt = paidAt;
    }

    public static PaymentConfirmResponse from(PaymentConfirmResult result) {
        return new PaymentConfirmResponse(
                result.getOrderId(),
                result.getTossPaymentKey(),
                result.getStatus().name(),
                result.getPaidAt()
        );
    }

    public Long getOrderId() { return orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public String getStatus() { return status; }
    public Instant getPaidAt() { return paidAt; }
}