package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.exception.ReserveFailedException;
import com.fandrops.order.domain.port.InventoryReservePort;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryReserveAdapter implements InventoryReservePort {

    private final InventoryCommandService inventoryCommandService;

    @Override
    public void reserve(Long productId, int quantity, Long orderId) {
        try {
            inventoryCommandService.reserve(orderId, productId, quantity);
        } catch (OutOfStockException e) {
            throw new com.fandrops.order.domain.exception.OutOfStockException(productId, e);
        } catch (ReserveFailedException | InventoryLockConflictException e) {
            throw new com.fandrops.order.domain.exception.ReserveConflictException(productId, e);
        }
    }
}
