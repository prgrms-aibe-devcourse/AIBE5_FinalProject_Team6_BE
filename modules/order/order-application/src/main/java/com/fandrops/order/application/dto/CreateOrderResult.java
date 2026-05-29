package com.fandrops.order.application.dto;

import lombok.Getter;

/** 주문 생성 결과. OrderService → Controller 반환용. */
@Getter
public class CreateOrderResult {

    private final Long orderId;
    private final String status;
    private final String orderPaymentKey;

    public CreateOrderResult(Long orderId, String status, String orderPaymentKey) {
        this.orderId = orderId;
        this.status = status;
        this.orderPaymentKey = orderPaymentKey;
    }
}
