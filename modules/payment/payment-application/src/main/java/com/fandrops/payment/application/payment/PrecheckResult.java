package com.fandrops.payment.application.payment;

import com.fandrops.payment.domain.payment.Payment;

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
}
