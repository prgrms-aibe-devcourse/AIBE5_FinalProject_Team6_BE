package com.fandrops.order.domain.exception;

/** 가용 재고가 0일 때. HTTP 409 OUT_OF_STOCK (retryable=false). */
public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId) {
        super(String.format("재고가 부족합니다: productId=%d", productId));
    }

    public OutOfStockException(Long productId, Throwable cause) {
        super(String.format("재고가 부족합니다: productId=%d", productId), cause);
    }
}
