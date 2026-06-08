package com.fandrops.order.application.dto;

import lombok.Getter;
import java.util.List;

@Getter
public class ProductListResponse {
    private final List<ProductListItemResponse> items;
    private final Long nextCursor;

    public ProductListResponse(List<ProductListItemResponse> items, Long nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
