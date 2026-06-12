package com.fandrops.order.domain.exception;

public class RestockAlertNotFoundException extends RuntimeException {
    public RestockAlertNotFoundException(Long fanId, Long productId) {
        super("재입고 알림 구독을 찾을 수 없습니다: fanId=" + fanId + ", productId=" + productId);
    }
}
