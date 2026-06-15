package com.fandrops.order.application.dto;

import java.util.List;
import lombok.Getter;

@Getter
public class OrderListResponse {

    private final List<OrderListItemResponse> items;
    private final Long nextCursor;

    public OrderListResponse(List<OrderListItemResponse> items, Long nextCursor) {
        this.items = items;
        this.nextCursor = nextCursor;
    }
}
