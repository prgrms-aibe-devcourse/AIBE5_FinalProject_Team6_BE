package com.fandrops.order.application.dto;

import com.fandrops.order.domain.ProductStatus;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class UpdateProductCommand {
    private final Long productId;
    private final String name;
    private final BigDecimal price;
    private final ProductStatus status;
    private final LocalDateTime dropsStartAt;
    private final LocalDateTime dropsEndAt;

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status) {
        this(productId, name, price, status, null, null);
    }

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.dropsStartAt = dropsStartAt;
        this.dropsEndAt = dropsEndAt;
    }
}
