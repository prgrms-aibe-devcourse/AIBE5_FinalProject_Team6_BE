package com.fandrops.inventory.application.exception;

public class InventoryNotFoundException extends RuntimeException {

    public static final String CODE = "INVENTORY_NOT_FOUND";

    public InventoryNotFoundException(Long productId) {
        super(String.format("재고를 찾을 수 없습니다: productId=%d", productId));
    }
}
