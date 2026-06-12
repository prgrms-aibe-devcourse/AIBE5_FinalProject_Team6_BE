package com.fandrops.payment.application.payment;

public interface TossPaymentPort {

    TossConfirmResult confirm(String tossPaymentKey, long amount, String orderPaymentKey);
}