package com.fandrops.payment.domain.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);

    /** tossPaymentKey(=DB payment_key) 멱등성 조회 — P-1 */
    Optional<Payment> findByTossPaymentKey(String tossPaymentKey);

    /** PENDING 상태이며 createdAt < threshold 인 결제 목록 — 15분 타임아웃 Job */
    List<Payment> findPendingOlderThan(Instant threshold);
}