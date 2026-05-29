package com.fandrops.order.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;

/** 주문 애그리게이트. create()로 PENDING 생성, reconstitute()로 DB 복원. */
@Getter
public class Order {

    private final Long id;
    private final Long fanId;
    private final List<OrderItem> items;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final String orderPaymentKey;
    private final String idempotencyKey;

    private Order(Long id, Long fanId, List<OrderItem> items, OrderStatus status, BigDecimal totalAmount
            , String orderPaymentKey, String idempotencyKey) {
        this.id = id;
        this.fanId = fanId;
        this.items = items;
        this.status = status;
        this.totalAmount = totalAmount;
        this.orderPaymentKey = orderPaymentKey;
        this.idempotencyKey = idempotencyKey;
    }

    public static Order create(Long fanId, List<OrderItem> items) {
        if (fanId == null) throw new IllegalArgumentException("fanId는 null일 수 없습니다");
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("주문 항목이 비었습니다");
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem item : items) {
            totalAmount = totalAmount.add(item.subtotal());
        }
        String orderPaymentKey = "opk_" + UUID.randomUUID().toString().replace("-", "");
        String idempotencyKey = UUID.randomUUID().toString();

        return new Order(null, fanId, List.copyOf(items), OrderStatus.PENDING, totalAmount, orderPaymentKey, idempotencyKey);
    }

    public static Order reconstitute(Long id, Long fanId, List<OrderItem> items, OrderStatus status,
                                     BigDecimal totalAmount, String orderPaymentKey, String idempotencyKey) {
        return new Order(id, fanId, List.copyOf(items), status, totalAmount, orderPaymentKey, idempotencyKey);
    }

}
