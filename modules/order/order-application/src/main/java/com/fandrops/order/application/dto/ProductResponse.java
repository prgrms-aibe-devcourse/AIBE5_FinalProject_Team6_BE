package com.fandrops.order.application.dto;

import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ProductResponse {
    private final Long id;
    private final Long artistId;
    private final String name;
    private final BigDecimal price;
    private final String status;
    private final int totalQty;
    private final int reservedQty;
    private final int availableQty;
    private final LocalDateTime updatedAt;

    public ProductResponse(Long id, Long artistId, String name, BigDecimal price,
                           String status, int totalQty, int reservedQty,
                           int availableQty, LocalDateTime updatedAt) {
        this.id = id;
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.totalQty = totalQty;
        this.reservedQty = reservedQty;
        this.availableQty = availableQty;
        this.updatedAt = updatedAt;
    }
}
