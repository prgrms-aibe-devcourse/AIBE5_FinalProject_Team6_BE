package com.fandrops.order.domain.exception;

import com.fandrops.order.domain.OrderStatus;

/** RESERVED가 아닌 주문을 취소 시도할 때. HTTP 409 ORDER_CANCELLATION_NOT_ALLOWED. */
public class OrderCancellationNotAllowedException extends RuntimeException {

    public OrderCancellationNotAllowedException(Long orderId, OrderStatus status) {
        super(String.format("취소할 수 없는 주문 상태입니다: orderId=%d, status=%s", orderId, status));
    }
}
