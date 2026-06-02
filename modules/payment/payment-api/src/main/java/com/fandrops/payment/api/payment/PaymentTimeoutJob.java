package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentTimeoutService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * invariants §4.1: PENDING 결제 15분 타임아웃 — ORDER cancel + 재고 원복.
 * QueueAdvanceScheduler와 동일 @Scheduled 패턴.
 */
@Component
public class PaymentTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentTimeoutJob.class);

    private final PaymentTimeoutService paymentTimeoutService;
    private final Duration paymentTimeout;

    public PaymentTimeoutJob(PaymentTimeoutService paymentTimeoutService,
                             @Value("${fandrops.order.payment-timeout:PT15M}") Duration paymentTimeout) {
        this.paymentTimeoutService = paymentTimeoutService;
        this.paymentTimeout = paymentTimeout;
    }

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        log.debug("결제 타임아웃 Job 실행: timeout={}", paymentTimeout);
        paymentTimeoutService.cancelTimedOutPayments(paymentTimeout);
    }
}