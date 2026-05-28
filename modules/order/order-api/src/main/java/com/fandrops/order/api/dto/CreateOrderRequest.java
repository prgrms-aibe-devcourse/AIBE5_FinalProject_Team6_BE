package com.fandrops.order.api.dto;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /orders 요청 바디. accessTicket과 주문 항목 목록을 받는다. */
@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    private String accessTicket;
    private List<OrderItemRequest> items;
}
