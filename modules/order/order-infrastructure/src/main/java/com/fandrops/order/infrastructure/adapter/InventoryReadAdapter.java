package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.port.InventoryReadPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryReadAdapter implements InventoryReadPort {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public InventoryInfo getByProductId(Long productId) {
        Inventory inventory = inventoryCommandService.getInventoryByProductId(productId);
        return new InventoryInfo(
                inventory.getTotalQty(),
                inventory.getReservedQty(),
                inventory.getAvailableQty());
    }
}
