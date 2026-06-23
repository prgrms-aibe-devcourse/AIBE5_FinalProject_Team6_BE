package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;
import java.util.Objects;

public record PrecheckResult(Payment payment, PaymentConfirmResult earlyReturn) {

    public static PrecheckResult proceed(Payment payment) {
        return new PrecheckResult(payment, null);
    }

    public static PrecheckResult done(PaymentConfirmResult result) {
        return new PrecheckResult(null, result);
    }

    public boolean isDone() {
        return earlyReturn != null;
    }

    @Override
    public Payment payment() {
        return Objects.requireNonNull(payment,
                "payment is null — isDone() == true 상태에서는 payment()에 접근할 수 없습니다");
    }

    @Override
    public PaymentConfirmResult earlyReturn() {
        return Objects.requireNonNull(earlyReturn,
                "earlyReturn is null — isDone() == false 상태에서는 earlyReturn()에 접근할 수 없습니다");
    }
}
