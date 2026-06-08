package com.fandrops.payment.domain.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);

    /** tossPaymentKey(=DB payment_key) 멱등성 조회 — P-1 */
    Optional<Payment> findByTossPaymentKey(String tossPaymentKey);

    /** 동시 웹훅 레이스 방지용 비관적 쓰기 락 조회 — 웹훅 처리 경로에서만 사용 */
    Optional<Payment> findByOrderIdForUpdate(Long orderId);

    /** PENDING 상태이며 createdAt < threshold 인 결제 목록 — 15분 타임아웃 Job */
    List<Payment> findPendingOlderThan(Instant threshold);

    /** 결제 단건 조회 — F07-02 결제 상세 */
    Optional<Payment> findById(Long id);
}