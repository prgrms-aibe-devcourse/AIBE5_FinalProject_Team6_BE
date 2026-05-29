package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    Optional<PaymentJpaEntity> findByPaymentKey(String paymentKey);

    List<PaymentJpaEntity> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant threshold);
}