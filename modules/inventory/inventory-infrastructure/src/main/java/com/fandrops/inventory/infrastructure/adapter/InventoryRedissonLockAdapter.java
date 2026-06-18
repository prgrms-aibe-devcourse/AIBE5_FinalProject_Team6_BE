package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Redisson 분산 락 + Read-Check-Write 재고 예약 전략.
 * atomic-update 전략과 k6 실측 비교용 대안 구현.
 *
 * 락 획득 → DB 조회 → domain.reserve() → JPA 저장 → TX 커밋 후 락 해제.
 * fandrops.inventory.lock-strategy=redisson 설정 시 활성화.
 *
 * 예외 매핑:
 *   - 락 timeout / interrupt      → InventoryLockConflictException (RESERVE_FAILED, retryable=true)
 *   - availableQty == 0           → OutOfStockException 전파 (OUT_OF_STOCK, retryable=false)
 *   - 0 < availableQty < qty      → ReserveFailedException 전파 (RESERVE_FAILED, retryable=true)
 *   - 상품 미존재                  → 0 반환 (InventoryCommandService → InventoryNotFoundException)
 */
@RequiredArgsConstructor
public class InventoryRedissonLockAdapter implements InventoryRepository {

    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 30;
    private static final String LOCK_KEY_PREFIX = "inventory:lock:";

    private final InventoryJpaRepository jpaRepository;
    private final RedissonClient redissonClient;

    @Override
    public int reserveAtomic(Long productId, int qty) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + productId);
        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new InventoryLockConflictException(productId);
            }
            // TX 커밋 완료 후 락 해제 — 커밋 전 해제 시 다른 노드가 구 상태를 읽을 수 있음
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            });

            Optional<InventoryJpaEntity> entityOpt = jpaRepository.findByProductId(productId);
            if (entityOpt.isEmpty()) return 0;

            Inventory inventory = entityOpt.get().toDomain();
            // OutOfStockException·ReserveFailedException 은 잡지 않고 전파
            // → InventoryReserveAdapter 가 각각 OUT_OF_STOCK·RESERVE_FAILED 로 변환
            inventory.reserve(qty, null);
            jpaRepository.save(InventoryJpaEntity.from(inventory));
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InventoryLockConflictException(productId);
        }
    }

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId).map(InventoryJpaEntity::toDomain);
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
