package com.fandrops.payment.api.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PaymentConfirmRequest {

    @NotNull
    private Long orderId;
    @NotBlank
    private String tossPaymentKey;
    @NotBlank
    private String orderPaymentKey;
    @Positive
    private long amount;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getTossPaymentKey() { return tossPaymentKey; }
    public void setTossPaymentKey(String tossPaymentKey) { this.tossPaymentKey = tossPaymentKey; }
    public String getOrderPaymentKey() { return orderPaymentKey; }
    public void setOrderPaymentKey(String orderPaymentKey) { this.orderPaymentKey = orderPaymentKey; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}