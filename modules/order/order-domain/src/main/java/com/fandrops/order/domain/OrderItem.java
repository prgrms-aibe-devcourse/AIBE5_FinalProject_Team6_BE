package com.fandrops.order.domain;

import java.math.BigDecimal;
import lombok.Getter;

/** 주문 항목 값 객체. productId·quantity·unitPrice를 묶고 소계를 계산한다. */
@Getter
public class OrderItem {

    private final Long productId;
    private final int quantity;
    private final BigDecimal unitPrice;

    public OrderItem(Long productId, int quantity, BigDecimal unitPrice) {

        if (productId == null) throw new IllegalArgumentException("productId는 null일 수 없습니다");
        if (quantity <= 0) throw new IllegalArgumentException("quantity는 양수여야 합니다: " + quantity);
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("unitPrice는 0 이상이어야 합니다");
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
