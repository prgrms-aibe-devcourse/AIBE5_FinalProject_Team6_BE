package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.order.domain.port.InventoryCreatePort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryCreateAdapter implements InventoryCreatePort {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void createInventory(Long productId, int totalQty) {
        inventoryCommandService.createInventory(productId, totalQty);
    }
}
