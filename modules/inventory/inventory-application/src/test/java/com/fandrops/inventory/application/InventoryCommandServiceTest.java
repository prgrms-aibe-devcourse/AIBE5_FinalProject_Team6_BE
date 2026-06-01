package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCommandService 단위 테스트")
class InventoryCommandServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;

    @InjectMocks
    private InventoryCommandService sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long RESTOCK_ID = 20L;

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("재고 예약 시 RESERVE 이력 저장")
        void reserve_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            sut.reserve(ORDER_ID, PRODUCT_ID, 10);

            verify(inventoryRepository).save(inventory);
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertEquals(InventoryChangeType.RESERVE, captor.getValue().getChangeType());
            assertEquals(ORDER_ID, captor.getValue().getReferenceId());
            assertEquals(10, captor.getValue().getDeltaQty());
        }

        @Test
        @DisplayName("품절 시 OutOfStockException 전파, save 미호출")
        void reserve_outOfStock_propagatesWithoutSave() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);
            inventory.reserve(5, ORDER_ID); // availableQty=0 셋업
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(OutOfStockException.class,
                    () -> sut.reserve(ORDER_ID, PRODUCT_ID, 1));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void reserve_inventoryNotFound() {
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.reserve(ORDER_ID, PRODUCT_ID, 10));
        }
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("재고 확정 시 DECREASE 이력 저장")
        void confirm_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            // reserve로 reservedQty=20 셋업; 반환된 history는 arrange 단계라 사용 안 함
            InventoryHistory ignored = inventory.reserve(20, ORDER_ID);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            sut.confirm(ORDER_ID, PRODUCT_ID, 20);

            verify(inventoryRepository).save(inventory);
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertEquals(InventoryChangeType.DECREASE, captor.getValue().getChangeType());
            assertEquals(ORDER_ID, captor.getValue().getReferenceId());
            assertEquals(20, captor.getValue().getDeltaQty());
        }

        @Test
        @DisplayName("reservedQty 부족 시 InvalidInventoryStateException 전파, save 미호출")
        void confirm_insufficientReserved_propagatesWithoutSave() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(InvalidInventoryStateException.class,
                    () -> sut.confirm(ORDER_ID, PRODUCT_ID, 10));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void confirm_inventoryNotFound() {
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.confirm(ORDER_ID, PRODUCT_ID, 20));
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("재고 복원 시 RELEASE 이력 저장")
        void restore_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            // reserve로 reservedQty=30 셋업; 반환된 history는 arrange 단계라 사용 안 함
            InventoryHistory ignored = inventory.reserve(30, ORDER_ID);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            sut.restore(ORDER_ID, PRODUCT_ID, 30);

            verify(inventoryRepository).save(inventory);
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertEquals(InventoryChangeType.RELEASE, captor.getValue().getChangeType());
            assertEquals(ORDER_ID, captor.getValue().getReferenceId());
            assertEquals(30, captor.getValue().getDeltaQty());
        }

        @Test
        @DisplayName("reservedQty 부족 시 InvalidInventoryStateException 전파, save 미호출")
        void restore_insufficientReserved_propagatesWithoutSave() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(InvalidInventoryStateException.class,
                    () -> sut.restore(ORDER_ID, PRODUCT_ID, 10));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void restore_inventoryNotFound() {
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.restore(ORDER_ID, PRODUCT_ID, 30));
        }
    }

    @Nested
    @DisplayName("increase()")
    class Increase {

        @Test
        @DisplayName("재입고 시 INCREASE 이력 저장")
        void increase_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            sut.increase(RESTOCK_ID, PRODUCT_ID, 50);

            verify(inventoryRepository).save(inventory);
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            assertEquals(InventoryChangeType.INCREASE, captor.getValue().getChangeType());
            assertEquals(RESTOCK_ID, captor.getValue().getReferenceId());
            assertEquals(50, captor.getValue().getDeltaQty());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void increase_inventoryNotFound() {
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.increase(RESTOCK_ID, PRODUCT_ID, 50));
        }
    }
}
