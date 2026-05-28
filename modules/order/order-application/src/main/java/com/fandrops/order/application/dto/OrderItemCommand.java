package com.fandrops.order.application.dto;

import lombok.Getter;

/** 주문 항목 하나의 입력값. Controller → OrderService 전달용. */
@Getter
public class OrderItemCommand {
    private final Long productId;
    private final int quantity;

    public OrderItemCommand(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}