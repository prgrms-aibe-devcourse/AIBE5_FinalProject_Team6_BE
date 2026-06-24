package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.DuplicateHistoryException;
import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryReadRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 낙관적 락 retry 지원을 위한 TX 경계 분리 Bean.
 *
 * reserve()는 @Transactional 없이 retry loop를 돌며 이 Bean의 reserveOnce()를 호출한다.
 * 각 호출마다 독립 TX가 열리므로 충돌로 인한 rollback-only 오염이 다음 시도에 영향을 주지 않는다.
 */
public class InventoryReserveTxHelper {

    private final InventoryReadRepository inventoryReadRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    public InventoryReserveTxHelper(InventoryReadRepository inventoryReadRepository,
                                    InventoryRepository inventoryRepository,
                                    InventoryHistoryRepository inventoryHistoryRepository) {
        this.inventoryReadRepository = inventoryReadRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryHistoryRepository = inventoryHistoryRepository;
    }

    /**
     * 단일 TX로 재고 예약 1회 시도.
     * InventoryLockConflictException / OutOfStockException / ReserveFailedException 은 그대로 전파.
     */
    @Transactional
    public void reserveOnce(Long orderId, Long productId, int qty) {
        int affected = inventoryRepository.reserveAtomic(productId, qty, orderId);
        if (affected == 0) {
            findByProductId(productId);
            throw new OutOfStockException(productId);
        }
        Inventory updated = findByProductId(productId);
        int qtyAfter = updated.getAvailableQty();
        InventoryHistory history = InventoryHistory.of(
                updated.getId(), InventoryChangeType.RESERVE, qty,
                qtyAfter + qty, qtyAfter, orderId, InventoryRefType.ORDER);
        saveHistory(history, updated.getId(), orderId);
    }

    private Inventory findByProductId(Long productId) {
        return inventoryReadRepository.findByProductId(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    private void saveHistory(InventoryHistory history, Long inventoryId, Long referenceId) {
        try {
            inventoryHistoryRepository.save(history);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateHistoryException(inventoryId, referenceId);
        }
    }
}
