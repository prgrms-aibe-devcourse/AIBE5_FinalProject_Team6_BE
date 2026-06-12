package com.fandrops.order.domain.exception;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(Long fanId) {
        super("장바구니를 찾을 수 없습니다: fanId=" + fanId);
    }
}
