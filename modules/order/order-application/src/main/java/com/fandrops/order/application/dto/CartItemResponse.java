package com.fandrops.order.application.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class CartItemResponse {
    private final Long cartItemId;
    private final Long productId;
    private final int quantity;
    private final BigDecimal price;

    public CartItemResponse(Long cartItemId, Long productId, int quantity, BigDecimal price) {
        this.cartItemId = cartItemId;
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }
}
