package com.fandrops.order.application.dto;

import lombok.Getter;

@Getter
public class AddCartItemCommand {
    private final Long fanId;
    private final Long productId;
    private final int quantity;

    public AddCartItemCommand(Long fanId, Long productId, int quantity) {
        this.fanId = fanId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
