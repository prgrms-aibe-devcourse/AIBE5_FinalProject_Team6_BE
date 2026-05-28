package com.fandrops.order.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/** 요청 바디 내 주문 항목 하나. productId와 quantity를 받는다. */
@Getter
@NoArgsConstructor
public class OrderItemRequest {

    private Long productId;
    private int quantity;
}
