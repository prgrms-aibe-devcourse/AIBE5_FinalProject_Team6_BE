package com.fandrops.payment.domain.payment;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);

    /** tossPaymentKey(=DB payment_key) 멱등성 조회 — P-1 */
    Optional<Payment> findByTossPaymentKey(String tossPaymentKey);
}