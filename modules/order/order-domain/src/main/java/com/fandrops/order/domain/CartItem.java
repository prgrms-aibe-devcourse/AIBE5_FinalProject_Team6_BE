package com.fandrops.order.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CartItem {
    private Long id;
    private Long cartId;
    private Long productId;
    private int quantity;
    private LocalDateTime addedAt;

    public CartItem(Long id, Long cartId, Long productId, int quantity, LocalDateTime addedAt) {
        this.id = id;
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.addedAt = addedAt;
    }

    public static CartItem create(Long cartId, Long productId, int quantity) {
        return new CartItem(null, cartId, productId, quantity, LocalDateTime.now());
    }

    public static CartItem of(Long id, Long cartId, Long productId, int quantity, LocalDateTime addedAt) {
        return new CartItem(id, cartId, productId, quantity, addedAt);
    }

    public void updateQuantity(int newQty) {
        if (newQty < 1) throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        if (newQty > 99) throw new IllegalArgumentException("수량은 99 이하이어야 합니다");
        this.quantity = newQty;
    }
}
