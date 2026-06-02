package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import com.fandrops.order.domain.port.InventoryRestorePort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryRestoreAdapter implements InventoryRestorePort {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void restore(Long productId, int quantity, Long orderId) {
        try {
            inventoryCommandService.restore(orderId, productId, quantity);
        } catch (InventoryLockConflictException e) {
            throw new ReserveConflictException(productId, e);
        }
    }
}
