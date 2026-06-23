package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import com.fandrops.payment.domain.payment.PaymentStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock private PaymentConfirmTxHelper txHelper;
    @Mock private TossPaymentPort tossPaymentPort;

    @InjectMocks private PaymentConfirmService service;

    private static final Long ORDER_ID = 1L;
    private static final String TOSS_KEY = "toss_pay_abc";
    private static final String ORDER_PAYMENT_KEY = "order-uuid-abc-123";
    private static final long AMOUNT = 50_000L;

    private final PaymentConfirmCommand command =
            new PaymentConfirmCommand(ORDER_ID, TOSS_KEY, ORDER_PAYMENT_KEY, AMOUNT);

    @Test
    @DisplayName("precheck done → PG 호출 없이 조기 반환")
    void earlyReturn_whenPrecheckIsDone() {
        Payment existing = Payment.reconstitute(1L, ORDER_ID, TOSS_KEY, AMOUNT, "카드",
                PaymentStatus.SUCCESS, Instant.now(), null, Instant.now(), 1);
        when(txHelper.precheck(command)).thenReturn(PrecheckResult.done(PaymentConfirmResult.from(existing)));

        PaymentConfirmResult result = service.confirm(command);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(tossPaymentPort, never()).confirm(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("precheck proceed + PG 성공 → applySuccess 호출")
    void pgSuccess_callsApplySuccess() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        when(txHelper.precheck(command)).thenReturn(PrecheckResult.proceed(payment));
        when(tossPaymentPort.confirm(TOSS_KEY, AMOUNT, ORDER_PAYMENT_KEY))
                .thenReturn(TossConfirmResult.success("카드", Instant.now()));
        Payment confirmed = Payment.reconstitute(1L, ORDER_ID, TOSS_KEY, AMOUNT, "카드",
                PaymentStatus.SUCCESS, Instant.now(), null, Instant.now(), 1);
        when(txHelper.applySuccess(eq(payment), eq(TOSS_KEY), eq("카드"), any(Instant.class)))
                .thenReturn(PaymentConfirmResult.from(confirmed));

        PaymentConfirmResult result = service.confirm(command);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("precheck proceed + PG 실패 → applyFailure 호출, PaymentConfirmFailedException 전파")
    void pgFailure_callsApplyFailure() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        when(txHelper.precheck(command)).thenReturn(PrecheckResult.proceed(payment));
        when(tossPaymentPort.confirm(TOSS_KEY, AMOUNT, ORDER_PAYMENT_KEY))
                .thenReturn(TossConfirmResult.failure("REJECT_CARD_COMPANY", "카드사 거절"));
        when(txHelper.applyFailure(eq(payment), eq("REJECT_CARD_COMPANY"), eq("카드사 거절")))
                .thenThrow(new PaymentConfirmFailedException("REJECT_CARD_COMPANY", "카드사 거절"));

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(PaymentConfirmFailedException.class);
    }

    @Test
    @DisplayName("PG 서버 오류 → TossPaymentUnavailableException 전파, applyFailure 미호출")
    void pgUnavailable_propagatesException() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        when(txHelper.precheck(command)).thenReturn(PrecheckResult.proceed(payment));
        doThrow(new TossPaymentUnavailableException("Toss PG 일시 오류: 500"))
                .when(tossPaymentPort).confirm(TOSS_KEY, AMOUNT, ORDER_PAYMENT_KEY);

        assertThatThrownBy(() -> service.confirm(command))
                .isInstanceOf(TossPaymentUnavailableException.class);
        verify(txHelper, never()).applyFailure(any(), anyString(), anyString());
    }
}
