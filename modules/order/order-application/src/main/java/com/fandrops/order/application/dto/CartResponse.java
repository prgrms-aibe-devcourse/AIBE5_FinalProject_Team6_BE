package com.fandrops.order.application.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class CartResponse {
    private final List<CartItemResponse> items;
    public CartResponse(List<CartItemResponse> items) {
        this.items = items;
    }
}
