package com.fandrops.order.application.dto;

import lombok.Getter;

@Getter
public class UpdateCartItemCommand {
    private final Long fanId;
    private final Long cartItemId;
    private final int quantity;

    public UpdateCartItemCommand(Long fanId, Long cartItemId, int quantity) {
        this.fanId = fanId;
        this.cartItemId = cartItemId;
        this.quantity = quantity;
    }
}
