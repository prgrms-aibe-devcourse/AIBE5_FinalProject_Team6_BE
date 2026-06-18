package com.fandrops.order.application.dto;

import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class ProductListItemResponse {
    private final Long id;
    private final Long artistId;
    private final String name;
    private final BigDecimal price;
    private final String status;
    private final int totalQty;
    private final int availableQty;

    public ProductListItemResponse(Long id, Long artistId, String name,
                                   BigDecimal price, String status,
                                   int totalQty, int availableQty) {
        this.id = id;
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.totalQty = totalQty;
        this.availableQty = availableQty;
    }

    public static ProductListItemResponse from(Product product, InventoryInfo inventory) {
        return new ProductListItemResponse(
                product.getId(), product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus().name(),
                inventory.getTotalQty(), inventory.getAvailableQty());
    }
}
