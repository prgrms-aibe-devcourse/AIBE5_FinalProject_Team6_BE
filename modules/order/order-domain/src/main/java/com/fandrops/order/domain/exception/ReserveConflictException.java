package com.fandrops.order.domain.exception;

/** 재고가 있지만 요청 수량을 선점하지 못했을 때. HTTP 409 RESERVE_FAILED (retryable=true). */
public class ReserveConflictException extends RuntimeException {

    public ReserveConflictException(Long productId) {
        super(String.format("재고 선점에 실패했습니다. 잠시 후 다시 시도해 주세요: productId=%d", productId));
    }
}
