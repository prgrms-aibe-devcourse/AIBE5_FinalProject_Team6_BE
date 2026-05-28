package com.fandrops.payment.domain.payment;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long ORDER_ID = 1L;
    private static final String TOSS_PAYMENT_KEY = "toss-pay-abc";
    private static final long AMOUNT = 50_000L;

    @Test
    @DisplayName("create: PENDING 상태로 생성")
    void create_returnsPaymentInPendingStatus() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getAmount()).isEqualTo(AMOUNT);
        assertThat(payment.getTossPaymentKey()).isNull();
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getFailedAt()).isNull();
    }

    @Test
    @DisplayName("confirm: PENDING → SUCCESS, P-2 paidAt NOT NULL")
    void confirm_transitionsToPendingSuccess() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        Instant now = Instant.now();

        payment.confirm(TOSS_PAYMENT_KEY, "카드", now);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getTossPaymentKey()).isEqualTo(TOSS_PAYMENT_KEY);
        assertThat(payment.getPaymentMethod()).isEqualTo("카드");
        assertThat(payment.getPaidAt()).isEqualTo(now);
        assertThat(payment.getFailedAt()).isNull();
    }

    @Test
    @DisplayName("fail: PENDING → FAILED, P-3 failedAt NOT NULL")
    void fail_transitionsToPendingFailed() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        Instant now = Instant.now();

        payment.fail(now);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedAt()).isEqualTo(now);
        assertThat(payment.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("P-4: SUCCESS 후 confirm 재호출 금지")
    void p4_cannotConfirmAfterSuccess() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        payment.confirm(TOSS_PAYMENT_KEY, "카드", Instant.now());

        assertThatThrownBy(() -> payment.confirm("another-key", "카드", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("P-4: SUCCESS 후 fail 호출 금지")
    void p4_cannotFailAfterSuccess() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        payment.confirm(TOSS_PAYMENT_KEY, "카드", Instant.now());

        assertThatThrownBy(() -> payment.fail(Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("P-4: FAILED 후 confirm 호출 금지")
    void p4_cannotConfirmAfterFailed() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);
        payment.fail(Instant.now());

        assertThatThrownBy(() -> payment.confirm(TOSS_PAYMENT_KEY, "카드", Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("create: orderId null 거부")
    void create_rejectsNullOrderId() {
        assertThatThrownBy(() -> Payment.create(null, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: amount 0 이하 거부")
    void create_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> Payment.create(ORDER_ID, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirm: paidAt null 거부 — P-2 보장")
    void confirm_rejectsNullPaidAt() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

        assertThatThrownBy(() -> payment.confirm(TOSS_PAYMENT_KEY, "카드", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fail: failedAt null 거부 — P-3 보장")
    void fail_rejectsNullFailedAt() {
        Payment payment = Payment.create(ORDER_ID, AMOUNT);

        assertThatThrownBy(() -> payment.fail(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}