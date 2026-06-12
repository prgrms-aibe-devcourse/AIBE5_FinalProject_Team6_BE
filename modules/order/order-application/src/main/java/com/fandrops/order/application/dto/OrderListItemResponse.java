package com.fandrops.order.application.dto;

import com.fandrops.order.domain.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class OrderListItemResponse {

    private final Long orderId;
    private final String status;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;

    private OrderListItemResponse(Long orderId, String status, BigDecimal totalAmount,
                                  LocalDateTime createdAt) {
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public static OrderListItemResponse from(Order order) {
        return new OrderListItemResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
