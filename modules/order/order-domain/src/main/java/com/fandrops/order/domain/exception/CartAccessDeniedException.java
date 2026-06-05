package com.fandrops.order.domain.exception;

public class CartAccessDeniedException extends RuntimeException {
    public CartAccessDeniedException() {
        super("본인의 장바구니 항목만 수정할 수 있습니다.");
    }
}
