package com.fandrops.order.application.dto;

import com.fandrops.order.domain.ProductImage;
import lombok.Getter;

@Getter
public class ProductImageResponse {
    private final String imageUrl;
    private final int sortOrder;
    /** Boolean(wrapper) 사용 — Lombok이 getIsPrimary() 생성 → Jackson이 "isPrimary"로 직렬화 */
    private final Boolean isPrimary;

    public ProductImageResponse(String imageUrl, int sortOrder, boolean isPrimary) {
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.isPrimary = isPrimary;
    }

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getImageUrl(), image.getSortOrder(), image.isPrimary());
    }
}
