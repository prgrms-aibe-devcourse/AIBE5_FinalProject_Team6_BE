package com.fandrops.order.application.dto;

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

    public ProductListItemResponse(Long id, Long artistId, String name,
                                   BigDecimal price, String status) {
        this.id = id;
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.status = status;
    }

    public static ProductListItemResponse from(Product product) {
        return new ProductListItemResponse(
                product.getId(), product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus().name());
    }
}
