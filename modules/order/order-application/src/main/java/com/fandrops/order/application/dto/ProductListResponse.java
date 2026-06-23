package com.fandrops.order.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import java.util.List;

@Getter
public class ProductListResponse {
    private final List<ProductListItemResponse> items;
    private final Long nextCursor;

    @JsonCreator
    public ProductListResponse(@JsonProperty("items") List<ProductListItemResponse> items,
                               @JsonProperty("nextCursor") Long nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
