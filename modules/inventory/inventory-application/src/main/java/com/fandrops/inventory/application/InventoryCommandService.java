package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.DuplicateHistoryException;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
        int qtyBefore = inventory.getAvailableQty();
        int affected = inventoryRepository.reserveAtomic(productId, qty);
        if (affected == 0) {
            throw new OutOfStockException(productId);
        }
        InventoryHistory history = InventoryHistory.of(
                inventory.getId(), InventoryChangeType.RESERVE, qty,
                qtyBefore, qtyBefore - qty, orderId, InventoryRefType.ORDER);
        saveHistory(history, inventory.getId(), orderId);
    }

    @Transactional
    public void confirm(Long orderId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.confirm(qty, orderId);
        try {
            inventoryRepository.save(inventory);
        } catch (OptimisticLockingFailureException e) {
            throw new InventoryLockConflictException(productId);
        }
        saveHistory(history, inventory.getId(), orderId);
    }

    @Transactional
    public void restore(Long orderId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.restore(qty, orderId);
        try {
            inventoryRepository.save(inventory);
        } catch (OptimisticLockingFailureException e) {
            throw new InventoryLockConflictException(productId);
        }
        saveHistory(history, inventory.getId(), orderId);
    }

    @Transactional
    public void increase(Long restockId, Long productId, int qty) {
        Inventory inventory = findByProductId(productId);
        InventoryHistory history = inventory.increase(qty, restockId);
        try {
            inventoryRepository.save(inventory);
        } catch (OptimisticLockingFailureException e) {
            throw new InventoryLockConflictException(productId);
        }
        saveHistory(history, inventory.getId(), restockId);
    }

    private void saveHistory(InventoryHistory history, Long inventoryId, Long referenceId) {
        try {
            inventoryHistoryRepository.save(history);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateHistoryException(inventoryId, referenceId);
        }
    }

    private Inventory findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }
}
