package com.fandrops.payment.infrastructure.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    Optional<PaymentJpaEntity> findByPaymentKey(String paymentKey);
}