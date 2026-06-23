package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

public class PaymentConfirmTxHelper {

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentConfirmTxHelper(PaymentRepository paymentRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public PrecheckResult precheck(PaymentConfirmCommand command) {
        // P-1: tossPaymentKey 기준 이미 처리된 결제 → 멱등 200
        return paymentRepository.findByTossPaymentKey(command.getTossPaymentKey())
                .map(existing -> PrecheckResult.done(PaymentConfirmResult.from(existing)))
                .orElseGet(() -> precheckByOrderId(command));
    }

    private PrecheckResult precheckByOrderId(PaymentConfirmCommand command) {
        // Payment 레코드가 없으면 lazy-create — POST /orders 시 생성 누락 대응
        Payment payment = paymentRepository.findByOrderId(command.getOrderId())
                .orElseGet(() -> paymentRepository.save(
                        Payment.create(command.getOrderId(), command.getAmount())));

        // orderId 기준 이미 SUCCESS → 멱등 200
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PrecheckResult.done(PaymentConfirmResult.from(payment));
        }

        // FAILED는 Transient — 타임아웃 Job이 CANCELLED 수렴 처리 중
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentAlreadyFailedException(command.getOrderId());
        }

        if (payment.getAmount() != command.getAmount()) {
            throw new IllegalArgumentException(
                    "결제 금액 불일치: 요청=" + command.getAmount() + ", 저장=" + payment.getAmount());
        }

        return PrecheckResult.proceed(payment);
    }

    @Transactional
    public PaymentConfirmResult applySuccess(Payment payment, String tossPaymentKey,
                                              String paymentMethod, Instant approvedAt) {
        payment.confirm(tossPaymentKey, paymentMethod, approvedAt);
        Payment saved = paymentRepository.save(payment);
        // AFTER_COMMIT 이벤트 — 형성빈 리스너: RESERVED→PAID, inventory confirm, PAID→COMPLETED
        eventPublisher.publishEvent(new PaymentApprovedEvent(payment.getOrderId()));
        return PaymentConfirmResult.from(saved);
    }

    // noRollbackFor: PaymentConfirmFailedException 발생해도 FAILED 상태를 DB에 커밋
    @Transactional(noRollbackFor = PaymentConfirmFailedException.class)
    public PaymentConfirmFailedException applyFailure(Payment payment,
                                                       String errorCode, String errorMessage) {
        payment.fail(Instant.now());
        paymentRepository.save(payment);
        // AFTER_COMMIT 이벤트 — 형성빈 리스너: RESERVED→FAILED, restore, FAILED→CANCELLED
        eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId()));
        throw new PaymentConfirmFailedException(errorCode, errorMessage);
    }
}
