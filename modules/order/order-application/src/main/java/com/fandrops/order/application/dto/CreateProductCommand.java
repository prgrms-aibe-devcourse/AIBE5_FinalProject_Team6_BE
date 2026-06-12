package com.fandrops.order.application.dto;

import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class CreateProductCommand {
    private final Long artistId;
    private final String name;
    private final BigDecimal price;
    private final int totalQty;
    private final LocalDateTime dropsStartAt;
    private final LocalDateTime dropsEndAt;

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty) {
        this(artistId, name, price, totalQty, null, null);
    }

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.totalQty = totalQty;
        this.dropsStartAt = dropsStartAt;
        this.dropsEndAt = dropsEndAt;
    }

    public boolean isDrops() {
        return dropsStartAt != null && dropsEndAt != null;
    }
}
