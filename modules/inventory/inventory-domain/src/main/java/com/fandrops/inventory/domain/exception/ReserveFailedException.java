package com.fandrops.inventory.domain.exception;

public class ReserveFailedException extends RuntimeException {

    public ReserveFailedException(Long productId, int requested, int available) {
        super(String.format(
            "재고 선점 실패: productId=%d, 요청=%d, 가용=%d",
            productId, requested, available
        ));
    }
}
