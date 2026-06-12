package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class PaymentTimeoutItemProcessor {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentTimeoutItemProcessor(PaymentRepository paymentRepository,
                                       ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Payment payment) {
        payment.fail(Instant.now());
        paymentRepository.save(payment);
        // AFTER_COMMIT 이벤트 — 형성빈 리스너: RESERVED→FAILED, restore, FAILED→CANCELLED
        eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId()));
    }
}