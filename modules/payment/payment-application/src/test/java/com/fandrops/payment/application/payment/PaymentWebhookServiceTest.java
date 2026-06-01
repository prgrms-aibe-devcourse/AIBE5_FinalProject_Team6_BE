package com.fandrops.payment.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentRepository;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    PaymentWebhookService sut;

    @BeforeEach
    void setUp() {
        sut = new PaymentWebhookService(paymentRepository, eventPublisher);
    }

    @Test
    @DisplayName("DONE 웹훅 수신 시 SUCCESS 전이 + PaymentApprovedEvent 발행")
    void handle_done_publishes_approved_event() {
        Payment payment = Payment.create(1L, 10_000L);
        when(paymentRepository.findByTossPaymentKey("key-done")).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        sut.handle(new PaymentWebhookCommand("key-done", "DONE", "카드", 10_000L, 1L, Instant.now()));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        ArgumentCaptor<PaymentApprovedEvent> captor = ArgumentCaptor.forClass(PaymentApprovedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ABORTED 웹훅 수신 시 FAILED 전이 + PaymentFailedEvent 발행")
    void handle_aborted_publishes_failed_event() {
        Payment payment = Payment.create(2L, 5_000L);
        when(paymentRepository.findByTossPaymentKey("key-aborted")).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(2L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        sut.handle(new PaymentWebhookCommand("key-aborted", "ABORTED", null, 5_000L, 2L, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("EXPIRED 웹훅 수신 시 FAILED 전이 + PaymentFailedEvent 발행")
    void handle_expired_publishes_failed_event() {
        Payment payment = Payment.create(3L, 3_000L);
        when(paymentRepository.findByTossPaymentKey("key-expired")).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(3L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        sut.handle(new PaymentWebhookCommand("key-expired", "EXPIRED", null, 3_000L, 3L, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("이미 SUCCESS인 tossPaymentKey 재수신 시 이벤트 미발행 (멱등)")
    void handle_duplicate_done_is_idempotent() {
        Payment payment = Payment.reconstitute(1L, 1L, "key-done", 10_000L, "카드",
                PaymentStatus.SUCCESS, Instant.now(), null, Instant.now(), 1);
        when(paymentRepository.findByTossPaymentKey("key-done")).thenReturn(Optional.of(payment));

        sut.handle(new PaymentWebhookCommand("key-done", "DONE", "카드", 10_000L, 1L, Instant.now()));

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("orderId 폴백 시 웹훅 금액이 DB 금액과 다르면 PaymentAmountMismatchException 발생")
    void handle_amount_mismatch_throws_exception() {
        Payment payment = Payment.create(5L, 10_000L);
        when(paymentRepository.findByTossPaymentKey("key-mismatch")).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(5L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                sut.handle(new PaymentWebhookCommand("key-mismatch", "DONE", "카드", 9_999L, 5L, Instant.now())))
                .isInstanceOf(PaymentAmountMismatchException.class)
                .hasMessageContaining("orderId=5");

        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("알 수 없는 tossStatus 수신 시 상태 변경 없음")
    void handle_unknown_status_does_nothing() {
        Payment payment = Payment.create(4L, 7_000L);
        when(paymentRepository.findByTossPaymentKey("key-unknown")).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderIdForUpdate(4L)).thenReturn(Optional.of(payment));

        sut.handle(new PaymentWebhookCommand("key-unknown", "CANCELED", null, 7_000L, 4L, null));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}