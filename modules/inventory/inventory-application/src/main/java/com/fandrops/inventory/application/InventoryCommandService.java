package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/** 재고 예약·확정·복원·증가를 단일 TX에서 처리하는 application 서비스. */
public class InventoryCommandService {

    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    public InventoryCommandService(InventoryRepository inventoryRepository,
                                   InventoryHistoryRepository inventoryHistoryRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryHistoryRepository = inventoryHistoryRepository;
    }

    @Transactional
    public void reserve(Long orderId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.reserve(qty, orderId);
        try {
            inventoryRepository.save(inventory);
        } catch (OptimisticLockingFailureException e) {
            throw new InventoryLockConflictException(productId);
        }
        inventoryHistoryRepository.save(history);
    }

    @Transactional
    public void confirm(Long orderId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.confirm(qty, orderId);
        inventoryRepository.save(inventory);
        inventoryHistoryRepository.save(history);
    }

    @Transactional
    public void restore(Long orderId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.restore(qty, orderId);
        inventoryRepository.save(inventory);
        inventoryHistoryRepository.save(history);
    }

    @Transactional
    public void increase(Long restockId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.increase(qty, restockId);
        inventoryRepository.save(inventory);
        inventoryHistoryRepository.save(history);
    }

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }
}
