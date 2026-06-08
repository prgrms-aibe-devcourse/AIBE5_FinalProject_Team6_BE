package com.fandrops.order.application.dto;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class CreateProductCommand {
    private final Long artistId;
    private final String name;
    private final BigDecimal price;
    private final int totalQty;

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty) {
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.totalQty = totalQty;
    }
}
