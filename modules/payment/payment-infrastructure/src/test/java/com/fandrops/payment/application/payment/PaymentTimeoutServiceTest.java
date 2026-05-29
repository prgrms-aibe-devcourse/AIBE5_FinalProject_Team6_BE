package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTimeoutServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentTimeoutItemProcessor itemProcessor;

    @InjectMocks private PaymentTimeoutService service;

    @Test
    @DisplayName("P-4: 타임아웃 결제 존재 시 itemProcessor.process 위임")
    void p4_timeoutJob_delegatesToProcessor() {
        Payment pendingPayment = Payment.create(1L, 50_000L);

        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of(pendingPayment));

        service.cancelTimedOutPayments(Duration.ofMinutes(15));

        verify(itemProcessor).process(pendingPayment);
    }

    @Test
    @DisplayName("P-4: 타임아웃 대상 없으면 processor 미호출")
    void p4_noTimeout_noCalls() {
        when(paymentRepository.findPendingOlderThan(any())).thenReturn(List.of());

        service.cancelTimedOutPayments(Duration.ofMinutes(15));

        verify(itemProcessor, never()).process(any());
    }
}