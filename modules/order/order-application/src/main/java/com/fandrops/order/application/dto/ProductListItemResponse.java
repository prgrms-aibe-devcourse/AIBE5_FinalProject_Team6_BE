package com.fandrops.order.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private final String thumbnailUrl;

    @JsonCreator
    public ProductListItemResponse(@JsonProperty("id") Long id,
                                   @JsonProperty("artistId") Long artistId,
                                   @JsonProperty("name") String name,
                                   @JsonProperty("price") BigDecimal price,
                                   @JsonProperty("status") String status,
                                   @JsonProperty("totalQty") int totalQty,
                                   @JsonProperty("availableQty") int availableQty,
                                   @JsonProperty("thumbnailUrl") String thumbnailUrl) {
        this.id = id;
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.totalQty = totalQty;
        this.availableQty = availableQty;
        this.thumbnailUrl = thumbnailUrl;
    }

    public static ProductListItemResponse from(Product product, InventoryInfo inventory, String thumbnailUrl) {
        return new ProductListItemResponse(
                product.getId(), product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus().name(),
                inventory.getTotalQty(), inventory.getAvailableQty(), thumbnailUrl);
    }
}
