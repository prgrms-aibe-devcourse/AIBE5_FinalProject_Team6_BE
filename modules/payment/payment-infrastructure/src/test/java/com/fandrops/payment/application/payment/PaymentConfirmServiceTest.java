package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private TossPaymentPort tossPaymentPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PaymentConfirmService service;

    private static final Long ORDER_ID = 1L;
    private static final String TOSS_KEY = "toss_pay_abc";
    private static final long AMOUNT = 50_000L;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.create(ORDER_ID, AMOUNT);
    }

    @Test
    @DisplayName("P-1: 동일 tossPaymentKey 재요청 → 기존 결제 결과 멱등 반환 (PG 재호출 없음)")
    void p1_sameTosskeyReturnsExisting() {
        Payment existing = Payment.create(ORDER_ID, AMOUNT);
        existing.confirm(TOSS_KEY, "카드", Instant.now());

        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.of(existing));

        PaymentConfirmResult result = service.confirm(new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, AMOUNT));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(tossPaymentPort, never()).confirm(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("P-2: confirm 성공 → Payment(SUCCESS) 저장, PaymentApprovedEvent 발행")
    void p2_confirmSuccess_savesSuccessAndPublishesEvent() {
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment));
        when(tossPaymentPort.confirm(TOSS_KEY, AMOUNT, ORDER_ID))
                .thenReturn(TossConfirmResult.success("카드", Instant.now()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfirmResult result = service.confirm(new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, AMOUNT));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getPaidAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(PaymentApprovedEvent.class));
    }

    @Test
    @DisplayName("P-3: confirm 실패 → Payment(FAILED) 저장, PaymentFailedEvent 발행")
    void p3_confirmFailure_savesFailedAndPublishesEvent() {
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment));
        when(tossPaymentPort.confirm(TOSS_KEY, AMOUNT, ORDER_ID))
                .thenReturn(TossConfirmResult.failure("REJECT_CARD_COMPANY", "카드사 거절"));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.confirm(new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, AMOUNT)))
                .isInstanceOf(PaymentConfirmFailedException.class)
                .hasMessageContaining("카드사 거절");

        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("orderId 기준 이미 SUCCESS → 멱등 200 반환")
    void alreadySuccess_idempotentReturn() {
        Payment successPayment = Payment.create(ORDER_ID, AMOUNT);
        successPayment.confirm(TOSS_KEY, "카드", Instant.now());

        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(successPayment));

        PaymentConfirmResult result = service.confirm(new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, AMOUNT));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(tossPaymentPort, never()).confirm(anyString(), anyLong(), anyLong());
    }
}