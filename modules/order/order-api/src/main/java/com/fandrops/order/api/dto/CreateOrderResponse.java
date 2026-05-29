package com.fandrops.order.api.dto;

import lombok.Getter;

/** POST /orders 성공 응답 data. orderId·status·orderPaymentKey를 반환한다. */
@Getter
public class CreateOrderResponse {

    private final Long orderId;
    private final String status;
    private final String orderPaymentKey;

    public CreateOrderResponse(Long orderId, String status, String orderPaymentKey) {
        this.orderId = orderId;
        this.status = status;
        this.orderPaymentKey = orderPaymentKey;
    }
}
