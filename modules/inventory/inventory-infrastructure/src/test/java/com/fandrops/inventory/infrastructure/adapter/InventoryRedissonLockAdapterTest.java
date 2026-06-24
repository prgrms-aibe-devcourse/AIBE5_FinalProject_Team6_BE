package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryRedissonLockAdapter 단위 테스트")
class InventoryRedissonLockAdapterTest {

    @Mock
    private InventoryJpaRepository jpaRepository;

    @Mock
    private RedissonClient redissonClient;

    @InjectMocks
    private InventoryRedissonLockAdapter sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final int QTY = 1;

    @BeforeEach
    void initTxSync() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTxSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private InventoryJpaEntity entityWithQty(int total, int reserved, int available) {
        return InventoryJpaEntity.from(
                Inventory.reconstitute(1L, PRODUCT_ID, total, reserved, available, 0));
    }

    @Nested
    @DisplayName("reserveAtomic()")
    class ReserveAtomic {

        private RLock lock;

        @BeforeEach
        void stubLock() throws InterruptedException {
            lock = mock(RLock.class);
            given(redissonClient.getLock(anyString())).willReturn(lock);
            given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
            given(lock.isHeldByCurrentThread()).willReturn(true);
        }

        @Test
        @DisplayName("락 획득 실패(timeout) → InventoryLockConflictException (RESERVE_FAILED, retryable=true)")
        void throwsLockConflict_whenLockNotAcquired() throws InterruptedException {
            given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

            assertThrows(InventoryLockConflictException.class,
                    () -> sut.reserveAtomic(PRODUCT_ID, QTY, ORDER_ID));
            verify(jpaRepository, never()).findByProductId(any());
        }

        @Test
        @DisplayName("상품 미존재 → 0 반환 (InventoryCommandService가 InventoryNotFoundException 처리)")
        void returns0_whenProductNotFound() {
            given(jpaRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertEquals(0, sut.reserveAtomic(PRODUCT_ID, QTY, ORDER_ID));
            verify(jpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 고갈(available=0) → OutOfStockException 전파 (OUT_OF_STOCK, retryable=false)")
        void throwsOutOfStock_whenStockExhausted() {
            given(jpaRepository.findByProductId(PRODUCT_ID))
                    .willReturn(Optional.of(entityWithQty(0, 0, 0)));

            assertThrows(OutOfStockException.class,
                    () -> sut.reserveAtomic(PRODUCT_ID, QTY, ORDER_ID));
            verify(jpaRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 충분(available=10 >= qty=1) → save 호출 후 1 반환")
        void returns1_whenStockSufficient() {
            InventoryJpaEntity entity = entityWithQty(10, 0, 10);
            given(jpaRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(entity));
            given(jpaRepository.save(any())).willReturn(entity);

            assertEquals(1, sut.reserveAtomic(PRODUCT_ID, QTY, ORDER_ID));
            verify(jpaRepository).save(any(InventoryJpaEntity.class));
        }
    }

    @Nested
    @DisplayName("confirmAtomic() / restoreAtomic() — JPA 원자적 UPDATE 위임")
    class DelegationMethods {

        @Test
        @DisplayName("confirmAtomic → jpaRepository.confirmAtomic 위임")
        void confirmAtomic_delegates() {
            given(jpaRepository.confirmAtomic(PRODUCT_ID, QTY)).willReturn(1);

            assertEquals(1, sut.confirmAtomic(PRODUCT_ID, QTY));
        }

        @Test
        @DisplayName("restoreAtomic → jpaRepository.restoreAtomic 위임")
        void restoreAtomic_delegates() {
            given(jpaRepository.restoreAtomic(PRODUCT_ID, QTY)).willReturn(1);

            assertEquals(1, sut.restoreAtomic(PRODUCT_ID, QTY));
        }
    }
}
