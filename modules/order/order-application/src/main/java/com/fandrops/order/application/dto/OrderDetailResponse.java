package com.fandrops.order.application.dto;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

@Getter
public class OrderDetailResponse {

    private final Long orderId;
    private final String status;
    private final BigDecimal totalAmount;
    private final String orderPaymentKey;
    private final List<OrderItemDetail> items;
    private final LocalDateTime createdAt;

    private OrderDetailResponse(Long orderId, String status, BigDecimal totalAmount,
                                String orderPaymentKey, List<OrderItemDetail> items,
                                LocalDateTime createdAt) {
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.orderPaymentKey = orderPaymentKey;
        this.items = items;
        this.createdAt = createdAt;
    }

    public static OrderDetailResponse from(Order order) {
        List<OrderItemDetail> itemDetails = order.getItems().stream()
                .map(OrderItemDetail::from)
                .toList();
        return new OrderDetailResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getOrderPaymentKey(),
                itemDetails,
                order.getCreatedAt()
        );
    }

    @Getter
    public static class OrderItemDetail {
        private final Long productId;
        private final int quantity;
        private final BigDecimal unitPrice;
        private final BigDecimal subtotal;

        private OrderItemDetail(Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.subtotal = subtotal;
        }

        public static OrderItemDetail from(OrderItem item) {
            return new OrderItemDetail(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.subtotal()
            );
        }
    }
}
