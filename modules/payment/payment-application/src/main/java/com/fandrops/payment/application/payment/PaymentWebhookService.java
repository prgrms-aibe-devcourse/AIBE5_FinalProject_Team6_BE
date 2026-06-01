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

public class PaymentWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentWebhookService(PaymentRepository paymentRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void handle(PaymentWebhookCommand command) {
        // P-1: tossPaymentKey 기준 이미 처리된 결제 → 멱등 200
        Optional<Payment> byTossKey = paymentRepository.findByTossPaymentKey(command.getTossPaymentKey());
        if (byTossKey.isPresent() && byTossKey.get().getStatus() != PaymentStatus.PENDING) {
            log.info("웹훅 중복 수신 무시: tossPaymentKey={}, status={}",
                    command.getTossPaymentKey(), byTossKey.get().getStatus());
            return;
        }

        Payment payment = byTossKey.isPresent()
                ? byTossKey.get()
                : findByOrderIdAndValidateAmount(command);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("이미 처리된 결제 상태: orderId={}, status={}", payment.getOrderId(), payment.getStatus());
            return;
        }

        if (isDone(command.getTossStatus())) {
            Instant approvedAt = command.getApprovedAt() != null ? command.getApprovedAt() : Instant.now();
            payment.confirm(command.getTossPaymentKey(), command.getMethod(), approvedAt);
            paymentRepository.save(payment);
            // AFTER_COMMIT — 형성빈 리스너: RESERVED→PAID, inventory confirm, PAID→COMPLETED
            eventPublisher.publishEvent(new PaymentApprovedEvent(payment.getOrderId()));
        } else if (isFailed(command.getTossStatus())) {
            payment.fail(Instant.now());
            paymentRepository.save(payment);
            // AFTER_COMMIT — 형성빈 리스너: RESERVED→FAILED, restore, FAILED→CANCELLED
            eventPublisher.publishEvent(new PaymentFailedEvent(payment.getOrderId()));
        } else {
            log.warn("처리하지 않는 웹훅 status 무시: tossPaymentKey={}, tossStatus={}",
                    command.getTossPaymentKey(), command.getTossStatus());
        }
    }

    private Payment findByOrderIdAndValidateAmount(PaymentWebhookCommand command) {
        Payment payment = paymentRepository.findByOrderId(command.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException(command.getOrderId()));
        if (payment.getAmount() != command.getAmount()) {
            log.error("웹훅 금액 불일치: orderId={}, dbAmount={}, webhookAmount={}",
                    command.getOrderId(), payment.getAmount(), command.getAmount());
            throw new PaymentAmountMismatchException(command.getOrderId(), payment.getAmount(), command.getAmount());
        }
        return payment;
    }

    private boolean isDone(String tossStatus) {
        return "DONE".equals(tossStatus);
    }

    private boolean isFailed(String tossStatus) {
        return "ABORTED".equals(tossStatus) || "EXPIRED".equals(tossStatus);
    }
}