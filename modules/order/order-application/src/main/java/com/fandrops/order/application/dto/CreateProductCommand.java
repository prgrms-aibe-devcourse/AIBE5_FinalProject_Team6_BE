package com.fandrops.order.application.dto;

import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class CreateProductCommand {
    private final Long artistId;
    private final String name;
    private final BigDecimal price;
    private final int totalQty;
    private final LocalDateTime dropsStartAt;
    private final LocalDateTime dropsEndAt;
    private final List<String> imageUrls;

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty) {
        this(artistId, name, price, totalQty, null, null, List.of());
    }

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        this(artistId, name, price, totalQty, dropsStartAt, dropsEndAt, List.of());
    }

    public CreateProductCommand(Long artistId, String name, BigDecimal price, int totalQty,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt,
                                List<String> imageUrls) {
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.totalQty = totalQty;
        this.dropsStartAt = dropsStartAt;
        this.dropsEndAt = dropsEndAt;
        this.imageUrls = imageUrls != null ? imageUrls : List.of();
    }

    public boolean isDrops() {
        return dropsStartAt != null && dropsEndAt != null;
    }
}
