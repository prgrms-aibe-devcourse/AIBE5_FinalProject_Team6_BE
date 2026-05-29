package com.fandrops.order.domain.exception;

/** 요청한 orderId에 해당하는 주문이 없을 때. HTTP 404 ORDER_NOT_FOUND. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super(String.format("주문을 찾을 수 없습니다: orderId=%d", orderId));
    }
}
