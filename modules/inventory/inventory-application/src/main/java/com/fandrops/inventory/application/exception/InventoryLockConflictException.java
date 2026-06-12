package com.fandrops.inventory.application.exception;

public class InventoryLockConflictException extends RuntimeException {

    public static final String CODE = "RESERVE_FAILED";

    public InventoryLockConflictException(Long productId) {
        super(String.format("재고 선점 충돌 (동시성): productId=%d", productId));
    }
}
