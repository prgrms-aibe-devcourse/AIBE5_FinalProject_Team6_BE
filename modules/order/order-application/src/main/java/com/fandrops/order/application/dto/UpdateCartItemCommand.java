package com.fandrops.order.application.dto;

import lombok.Getter;

@Getter
public class UpdateCartItemCommand {
    private final Long cartItemId;
    private final int quantity;

    public UpdateCartItemCommand(Long cartItemId, int quantity) {
        this.cartItemId = cartItemId;
        this.quantity = quantity;
    }
}
