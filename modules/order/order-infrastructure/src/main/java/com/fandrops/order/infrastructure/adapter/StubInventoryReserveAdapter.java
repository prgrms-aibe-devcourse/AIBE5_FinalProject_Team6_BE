package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.port.InventoryReservePort;

/** InventoryReservePort 스텁 구현체. feat/24 머지 후 실제 구현으로 교체 예정. */
public class StubInventoryReserveAdapter implements InventoryReservePort {

    // TODO: feat/24 머지 후 inventory-application의 InventoryCommandService 실제 구현으로 교체
    @Override
    public void reserve(Long productId, int quantity, Long orderId) {
        // 스텁: 항상 성공
    }
}
