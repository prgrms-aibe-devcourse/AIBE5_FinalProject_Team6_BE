package com.fandrops.order.application.dto;

import java.util.List;
import lombok.Getter;

/** POST /orders 주문 생성 요청 입력값. fanId·accessTicket·items를 묶는다. */
@Getter
public class CreateOrderCommand {
    private final Long fanId;
    private final String accessTicket;
    private final List<OrderItemCommand> items;

    public CreateOrderCommand(Long fanId, String accessTicket, List<OrderItemCommand> items) {
        this.fanId = fanId;
        this.accessTicket = accessTicket;
        this.items = items;
    }
}
