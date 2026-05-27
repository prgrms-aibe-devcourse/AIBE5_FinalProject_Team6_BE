package com.fandrops.inventory.domain.exception;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId) {
        super(String.format("품절: productId=%d", productId));
    }
}
