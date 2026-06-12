package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.DuplicateHistoryException;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
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
        int affected = inventoryRepository.reserveAtomic(productId, qty);
        if (affected == 0) {
            findByProductId(productId);  // InventoryNotFoundException 체크
            throw new OutOfStockException(productId);
        }
        // clearAutomatically=true → JPA 캐시 클리어됨, 재조회로 정확한 post-update 값 획득
        Inventory updated = findByProductId(productId);
        int qtyAfter = updated.getAvailableQty();
        InventoryHistory history = InventoryHistory.of(
                updated.getId(), InventoryChangeType.RESERVE, qty,
                qtyAfter + qty, qtyAfter, orderId, InventoryRefType.ORDER);
        saveHistory(history, updated.getId(), orderId);
    }

    @Transactional
    public void confirm(Long orderId, Long productId, int qty) {
        // 멱등성 가드: DECREASE 이력이 이미 존재하면 재처리(서버 재시작·중복 이벤트) — 정상 종료
        if (inventoryHistoryRepository.existsByReferenceIdAndRefTypeAndChangeType(
                orderId, InventoryRefType.ORDER, InventoryChangeType.DECREASE)) {
            return;
        }
        int affected = inventoryRepository.confirmAtomic(productId, qty);
        if (affected == 0) {
            Inventory inventory = findByProductId(productId);
            throw new InvalidInventoryStateException(
                    String.format("confirm 실패: reservedQty=%d, qty=%d", inventory.getReservedQty(), qty));
        }
        Inventory updated = findByProductId(productId);
        int qtyAfter = updated.getTotalQty();
        InventoryHistory history = InventoryHistory.of(
                updated.getId(), InventoryChangeType.DECREASE, qty,
                qtyAfter + qty, qtyAfter, orderId, InventoryRefType.ORDER);
        saveHistory(history, updated.getId(), orderId);
    }

    @Transactional
    public void restore(Long orderId, Long productId, int qty) {
        // 멱등성 가드: RELEASE 이력이 이미 존재하면 재처리(서버 재시작·중복 이벤트) — 정상 종료
        if (inventoryHistoryRepository.existsByReferenceIdAndRefTypeAndChangeType(
                orderId, InventoryRefType.ORDER, InventoryChangeType.RELEASE)) {
            return;
        }
        int affected = inventoryRepository.restoreAtomic(productId, qty);
        if (affected == 0) {
            Inventory inventory = findByProductId(productId);
            throw new InvalidInventoryStateException(
                    String.format("restore 실패: reservedQty=%d, qty=%d", inventory.getReservedQty(), qty));
        }
        Inventory updated = findByProductId(productId);
        int qtyAfter = updated.getAvailableQty();
        InventoryHistory history = InventoryHistory.of(
                updated.getId(), InventoryChangeType.RELEASE, qty,
                qtyAfter - qty, qtyAfter, orderId, InventoryRefType.ORDER);
        saveHistory(history, updated.getId(), orderId);
    }

    @Transactional
    public void createInventory(Long productId, int totalQty) {
        Inventory inventory = Inventory.create(productId, totalQty);
        inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Inventory getInventoryByProductId(Long productId) {
        return findByProductId(productId);
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
