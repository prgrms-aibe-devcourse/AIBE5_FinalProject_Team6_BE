package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.DuplicateHistoryException;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryReadRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.ThreadLocalRandom;

/** 재고 예약·확정·복원·증가를 처리하는 application 서비스. */
public class InventoryCommandService {

    private static final int RESERVE_MAX_ATTEMPTS = 5;

    private final InventoryReadRepository inventoryReadRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;
    private final InventoryReserveTxHelper reserveTxHelper;

    public InventoryCommandService(InventoryReadRepository inventoryReadRepository,
                                   InventoryRepository inventoryRepository,
                                   InventoryHistoryRepository inventoryHistoryRepository,
                                   InventoryReserveTxHelper reserveTxHelper) {
        this.inventoryReadRepository = inventoryReadRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryHistoryRepository = inventoryHistoryRepository;
        this.reserveTxHelper = reserveTxHelper;
    }

    /**
     * 낙관적 락 충돌 시 bounded retry(최대 5회, jitter 10~50ms).
     * TX 없이 retry loop를 돌고 단일 시도는 reserveTxHelper.reserveOnce()에 위임한다.
     * 재시도 소진 후에도 실패 시 InventoryLockConflictException → 409 RESERVE_FAILED.
     */
    public void reserve(Long orderId, Long productId, int qty) {
        InventoryLockConflictException lastConflict = null;
        for (int attempt = 0; attempt < RESERVE_MAX_ATTEMPTS; attempt++) {
            try {
                reserveTxHelper.reserveOnce(orderId, productId, qty);
                return;
            } catch (InventoryLockConflictException e) {
                lastConflict = e;
                if (attempt < RESERVE_MAX_ATTEMPTS - 1) {
                    long backoff = 10 + ThreadLocalRandom.current().nextLong(41); // 10~50ms
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw lastConflict;
                    }
                }
            }
        }
        throw lastConflict;
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

    @Transactional(readOnly = true)
    public Map<Long, Inventory> getInventoryByProductIds(List<Long> productIds) {
        return inventoryReadRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Inventory::getProductId, i -> i));
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
        return inventoryReadRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }
}
