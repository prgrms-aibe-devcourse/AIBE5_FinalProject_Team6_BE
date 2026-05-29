package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentTimeoutItemProcessor itemProcessor;

    public PaymentTimeoutService(PaymentRepository paymentRepository,
                                 PaymentTimeoutItemProcessor itemProcessor) {
        this.paymentRepository = paymentRepository;
        this.itemProcessor = itemProcessor;
    }

    public void cancelTimedOutPayments(Duration timeout) {
        Instant threshold = Instant.now().minus(timeout);
        List<Payment> timedOut = paymentRepository.findPendingOlderThan(threshold);

        for (Payment payment : timedOut) {
            try {
                itemProcessor.process(payment);
                log.info("결제 타임아웃 처리 완료: orderId={}", payment.getOrderId());
            } catch (Exception e) {
                log.warn("결제 타임아웃 처리 실패: orderId={}", payment.getOrderId(), e);
            }
        }
    }
}