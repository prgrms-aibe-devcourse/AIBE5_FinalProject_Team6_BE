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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmTxHelperTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PaymentConfirmTxHelper txHelper;

    private static final Long ORDER_ID = 1L;
    private static final String TOSS_KEY = "toss_pay_abc";
    private static final String ORDER_PAYMENT_KEY = "order-uuid-abc-123";
    private static final long AMOUNT = 50_000L;

    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.create(ORDER_ID, AMOUNT);
    }

    // --- precheck ---

    @Test
    @DisplayName("P-1: 동일 tossPaymentKey 재요청 → done(기존 결과) 반환")
    void precheck_p1_sameTosskeyReturnsDone() {
        Payment existing = Payment.create(ORDER_ID, AMOUNT);
        existing.confirm(TOSS_KEY, "카드", Instant.now());
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.of(existing));

        PrecheckResult result = txHelper.precheck(cmd());

        assertThat(result.isDone()).isTrue();
        assertThat(result.earlyReturn().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("orderId 기준 이미 SUCCESS → done 반환")
    void precheck_alreadySuccess_returnsDone() {
        Payment successPayment = Payment.create(ORDER_ID, AMOUNT);
        successPayment.confirm(TOSS_KEY, "카드", Instant.now());
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(successPayment));

        PrecheckResult result = txHelper.precheck(cmd());

        assertThat(result.isDone()).isTrue();
    }

    @Test
    @DisplayName("P-5: FAILED 상태 → PaymentAlreadyFailedException")
    void precheck_p5_failedThrows() {
        Payment failedPayment = Payment.create(ORDER_ID, AMOUNT);
        failedPayment.fail(Instant.now());
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(failedPayment));

        assertThatThrownBy(() -> txHelper.precheck(cmd()))
                .isInstanceOf(PaymentAlreadyFailedException.class);
    }

    @Test
    @DisplayName("P-6: 금액 불일치 → IllegalArgumentException")
    void precheck_p6_amountMismatchThrows() {
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> txHelper.precheck(
                new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, ORDER_PAYMENT_KEY, AMOUNT + 1_000L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액 불일치");
    }

    @Test
    @DisplayName("P-8: Payment 레코드 미존재 → lazy-create 후 proceed 반환")
    void precheck_p8_lazyCreate_returnsProceed() {
        when(paymentRepository.findByTossPaymentKey(TOSS_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PrecheckResult result = txHelper.precheck(cmd());

        assertThat(result.isDone()).isFalse();
        assertThat(result.payment()).isNotNull();
    }

    // --- applySuccess ---

    @Test
    @DisplayName("applySuccess → SUCCESS 저장, PaymentApprovedEvent 발행")
    void applySuccess_savesSuccessAndPublishesEvent() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentConfirmResult result = txHelper.applySuccess(pendingPayment, TOSS_KEY, "카드", Instant.now());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.getPaidAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(PaymentApprovedEvent.class));
    }

    // --- applyFailure ---

    @Test
    @DisplayName("applyFailure → FAILED 저장, PaymentFailedEvent 발행, PaymentConfirmFailedException throw")
    void applyFailure_savesFailedAndThrows() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() ->
                txHelper.applyFailure(pendingPayment, "REJECT_CARD_COMPANY", "카드사 거절"))
                .isInstanceOf(PaymentConfirmFailedException.class)
                .hasMessageContaining("카드사 거절");

        verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
    }

    private PaymentConfirmCommand cmd() {
        return new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, ORDER_PAYMENT_KEY, AMOUNT);
    }
}
