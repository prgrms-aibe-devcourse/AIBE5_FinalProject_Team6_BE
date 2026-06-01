package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    Optional<PaymentJpaEntity> findByPaymentKey(String paymentKey);

    List<PaymentJpaEntity> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant threshold);
}