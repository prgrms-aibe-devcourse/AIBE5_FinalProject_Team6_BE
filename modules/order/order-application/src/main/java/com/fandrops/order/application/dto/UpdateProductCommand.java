package com.fandrops.order.application.dto;

import com.fandrops.order.domain.ProductStatus;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class UpdateProductCommand {
    private final Long productId;
    private final String name;
    private final BigDecimal price;
    private final ProductStatus status;

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.status = status;
    }
}
