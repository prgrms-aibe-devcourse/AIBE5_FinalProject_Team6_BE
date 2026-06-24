package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryReadRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReserveTxHelper 단위 테스트")
class InventoryReserveTxHelperTest {

    @Mock
    private InventoryReadRepository inventoryReadRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;

    @InjectMocks
    private InventoryReserveTxHelper sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 10L;

    @Nested
    @DisplayName("reserveOnce()")
    class ReserveOnce {

        @Test
        @DisplayName("Atomic Update 성공 시 RESERVE 이력 저장, save() 미호출")
        void reserveOnce_savesHistoryWithoutDirectSave() {
            Inventory postUpdate = Inventory.reconstitute(1L, PRODUCT_ID, 100, 10, 90, 0);
            given(inventoryRepository.reserveAtomic(eq(PRODUCT_ID), eq(10), eq(ORDER_ID))).willReturn(1);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(postUpdate));

            sut.reserveOnce(ORDER_ID, PRODUCT_ID, 10);

            verify(inventoryRepository, never()).save(any());
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            InventoryHistory saved = captor.getValue();
            assertEquals(InventoryChangeType.RESERVE, saved.getChangeType());
            assertEquals(ORDER_ID, saved.getReferenceId());
            assertEquals(10, saved.getDeltaQty());
            assertEquals(100, saved.getQtyBefore());
            assertEquals(90, saved.getQtyAfter());
        }

        @Test
        @DisplayName("Atomic Update 0 rows(재고 부족) 시 OutOfStockException, 이력 미저장")
        void reserveOnce_atomicUpdateZeroRows_throwsOutOfStock() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);
            given(inventoryRepository.reserveAtomic(eq(PRODUCT_ID), eq(10), eq(ORDER_ID))).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(OutOfStockException.class,
                    () -> sut.reserveOnce(ORDER_ID, PRODUCT_ID, 10));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void reserveOnce_inventoryNotFound() {
            given(inventoryRepository.reserveAtomic(eq(PRODUCT_ID), eq(10), eq(ORDER_ID))).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.reserveOnce(ORDER_ID, PRODUCT_ID, 10));
        }
    }
}
