package com.fandrops.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /orders 요청 바디. accessTicket과 주문 항목 목록을 받는다. */
@Getter
@NoArgsConstructor
public class CreateOrderRequest {

    @NotNull
    private String accessTicket;

    @NotEmpty
    private List<@Valid OrderItemRequest> items;
}
