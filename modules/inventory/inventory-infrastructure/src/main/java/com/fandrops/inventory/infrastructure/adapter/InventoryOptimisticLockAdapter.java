package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Optional;

/**
 * JPA @Version 낙관적 락 + Read-Check-Write 재고 예약 전략.
 *
 * DB 조회 → domain.reserve() → saveAndFlush(version 검사) → 충돌 시 InventoryLockConflictException.
 * fandrops.inventory.lock-strategy=optimistic 설정 시 활성화.
 *
 * 예외 매핑:
 *   - @Version 충돌                → entityManager.clear() + InventoryLockConflictException (retryable=true)
 *   - availableQty == 0           → OutOfStockException 전파 (OUT_OF_STOCK, retryable=false)
 *   - 0 < availableQty < qty      → ReserveFailedException 전파 (RESERVE_FAILED, retryable=true)
 *   - 상품 미존재                  → 0 반환 (InventoryCommandService → InventoryNotFoundException)
 */
@RequiredArgsConstructor
public class InventoryOptimisticLockAdapter implements InventoryRepository {

    private final InventoryJpaRepository jpaRepository;
    private final EntityManager entityManager;

    @Override
    public int reserveAtomic(Long productId, int qty, Long orderId) {
        Optional<InventoryJpaEntity> entityOpt = jpaRepository.findByProductId(productId);
        if (entityOpt.isEmpty()) return 0;

        Inventory inventory = entityOpt.get().toDomain();
        // OutOfStockException·ReserveFailedException 은 잡지 않고 전파
        inventory.reserve(qty, orderId);
        try {
            // saveAndFlush — 즉시 플러시하여 버전 충돌을 어댑터 안에서 감지
            jpaRepository.saveAndFlush(InventoryJpaEntity.from(inventory));
            return 1;
        } catch (OptimisticLockingFailureException e) {
            // 세션에 남은 더티 엔티티가 TX 커밋 시 재플러시되지 않도록 세션 초기화
            entityManager.clear();
            throw new InventoryLockConflictException(productId);
        }
    }

    @Override
    public void save(Inventory inventory) {
        jpaRepository.save(InventoryJpaEntity.from(inventory));
    }

    @Override
    public int confirmAtomic(Long productId, int qty) {
        return jpaRepository.confirmAtomic(productId, qty);
    }

    @Override
    public int restoreAtomic(Long productId, int qty) {
        return jpaRepository.restoreAtomic(productId, qty);
    }
}
