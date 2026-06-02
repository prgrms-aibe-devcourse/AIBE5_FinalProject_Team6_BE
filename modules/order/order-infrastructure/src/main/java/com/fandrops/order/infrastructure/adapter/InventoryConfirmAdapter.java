package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryConfirmAdapter implements InventoryConfirmPort {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void confirm(Long productId, int quantity, Long orderId) {
        try {
            inventoryCommandService.confirm(orderId, productId, quantity);
        } catch (InventoryLockConflictException e) {
            throw new ReserveConflictException(productId, e);
        }
    }
}
