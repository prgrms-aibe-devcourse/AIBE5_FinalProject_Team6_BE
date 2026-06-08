package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

public class PaymentConfirmService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmService.class);

    private final PaymentRepository paymentRepository;
    private final TossPaymentPort tossPaymentPort;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentConfirmService(PaymentRepository paymentRepository,
                                 TossPaymentPort tossPaymentPort,
                                 ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.tossPaymentPort = tossPaymentPort;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(noRollbackFor = PaymentConfirmFailedException.class)
    public PaymentConfirmResult confirm(PaymentConfirmCommand command) {
        // P-1: tossPaymentKey 기준 이미 처리된 결제 → 멱등 200
        Optional<Payment> byTossKey = paymentRepository.findByTossPaymentKey(command.getTossPaymentKey());
        if (byTossKey.isPresent()) {
            return PaymentConfirmResult.from(byTossKey.get());
        }

        // Payment 레코드가 없으면 lazy-create — POST /orders 시 생성 누락 대응
        Payment payment = paymentRepository.findByOrderId(command.getOrderId())
                .orElseGet(() -> paymentRepository.save(
                        Payment.create(command.getOrderId(), command.getAmount())));

        // orderId 기준 이미 SUCCESS → 멱등 200
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PaymentConfirmResult.from(payment);
        }

        // FAILED는 Transient — 타임아웃 Job이 CANCELLED 수렴 처리 중
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentAlreadyFailedException(command.getOrderId());
        }

        if (payment.getAmount() != command.getAmount()) {
            throw new IllegalArgumentException(
                    "결제 금액 불일치: 요청=" + command.getAmount() + ", 저장=" + payment.getAmount());
        }

        // confirm 진행 중 동시 중복: @Version 낙관적 락으로 방어 (Toss PG 멱등 보장)
        TossConfirmResult pgResult = tossPaymentPort.confirm(
                command.getTossPaymentKey(), command.getAmount(), command.getOrderId());

        if (pgResult.isSuccess()) {
            payment.confirm(command.getTossPaymentKey(), pgResult.getPaymentMethod(), pgResult.getApprovedAt());
            Payment saved = paymentRepository.save(payment);
            // AFTER_COMMIT 이벤트 — 형성빈 리스너: RESERVED→PAID, inventory confirm, PAID→COMPLETED
            eventPublisher.publishEvent(new PaymentApprovedEvent(command.getOrderId()));
            return PaymentConfirmResult.from(saved);
        } else {
            payment.fail(Instant.now());
            paymentRepository.save(payment);
            // AFTER_COMMIT 이벤트 — 형성빈 리스너: RESERVED→FAILED, restore, FAILED→CANCELLED
            eventPublisher.publishEvent(new PaymentFailedEvent(command.getOrderId()));
            throw new PaymentConfirmFailedException(pgResult.getErrorCode(), pgResult.getErrorMessage());
        }
    }
}