package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
        @DisplayName("재고 예약 시 도메인 연산 후 save 호출")
        void reserve_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));
            given(inventoryRepository.save(any())).willReturn(inventory);

            sut.reserve(ORDER_ID, PRODUCT_ID, 10);

            verify(inventoryRepository).save(inventory);
            verify(inventoryHistoryRepository).save(any(InventoryHistory.class));
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
        @DisplayName("재고 확정 시 도메인 연산 후 save 호출")
        void confirm_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            inventory.reserve(20, ORDER_ID);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));
            given(inventoryRepository.save(any())).willReturn(inventory);

            sut.confirm(ORDER_ID, PRODUCT_ID, 20);

            verify(inventoryRepository).save(inventory);
            verify(inventoryHistoryRepository).save(any(InventoryHistory.class));
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
        @DisplayName("재고 복원 시 도메인 연산 후 save 호출")
        void restore_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            inventory.reserve(30, ORDER_ID);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));
            given(inventoryRepository.save(any())).willReturn(inventory);

            sut.restore(ORDER_ID, PRODUCT_ID, 30);

            verify(inventoryRepository).save(inventory);
            verify(inventoryHistoryRepository).save(any(InventoryHistory.class));
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
        @DisplayName("재입고 시 도메인 연산 후 save 호출")
        void increase_savesInventoryAndHistory() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));
            given(inventoryRepository.save(any())).willReturn(inventory);

            sut.increase(PRODUCT_ID, 50, RESTOCK_ID);

            verify(inventoryRepository).save(inventory);
            verify(inventoryHistoryRepository).save(any(InventoryHistory.class));
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void increase_inventoryNotFound() {
            given(inventoryRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.increase(PRODUCT_ID, 50, RESTOCK_ID));
        }
    }
}
