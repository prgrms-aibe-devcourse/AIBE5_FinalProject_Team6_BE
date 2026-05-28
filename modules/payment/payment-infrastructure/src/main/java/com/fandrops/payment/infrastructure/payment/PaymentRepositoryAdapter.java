package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        return jpaRepository.save(PaymentJpaEntity.fromDomain(payment)).toDomain();
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId).map(PaymentJpaEntity::toDomain);
    }

    @Override
    public Optional<Payment> findByTossPaymentKey(String tossPaymentKey) {
        return jpaRepository.findByPaymentKey(tossPaymentKey).map(PaymentJpaEntity::toDomain);
    }
}