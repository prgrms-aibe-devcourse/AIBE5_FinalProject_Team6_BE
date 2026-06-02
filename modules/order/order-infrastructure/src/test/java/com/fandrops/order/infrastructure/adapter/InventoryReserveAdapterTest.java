package com.fandrops.order.infrastructure.adapter;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.application.exception.InventoryLockConflictException;
import com.fandrops.inventory.domain.exception.ReserveFailedException;
import com.fandrops.order.domain.exception.OutOfStockException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryReserveAdapter 단위 테스트")
class InventoryReserveAdapterTest {

    @Mock
    private InventoryCommandService inventoryCommandService;

    @InjectMocks
    private InventoryReserveAdapter sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final int QTY = 5;

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("정상 예약 시 InventoryCommandService.reserve() 호출")
        void reserve_success() {
            willDoNothing().given(inventoryCommandService).reserve(ORDER_ID, PRODUCT_ID, QTY);

            sut.reserve(PRODUCT_ID, QTY, ORDER_ID);

            verify(inventoryCommandService).reserve(ORDER_ID, PRODUCT_ID, QTY);
        }

        @Test
        @DisplayName("inventory.OutOfStockException → order.OutOfStockException 변환")
        void reserve_outOfStock_mapsToOrderException() {
            willThrow(new com.fandrops.inventory.domain.exception.OutOfStockException(PRODUCT_ID))
                    .given(inventoryCommandService).reserve(ORDER_ID, PRODUCT_ID, QTY);

            assertThrows(OutOfStockException.class,
                    () -> sut.reserve(PRODUCT_ID, QTY, ORDER_ID));
        }

        @Test
        @DisplayName("inventory.ReserveFailedException → order.ReserveConflictException 변환")
        void reserve_reserveFailed_mapsToConflictException() {
            willThrow(new ReserveFailedException(PRODUCT_ID, QTY, 2))
                    .given(inventoryCommandService).reserve(ORDER_ID, PRODUCT_ID, QTY);

            assertThrows(ReserveConflictException.class,
                    () -> sut.reserve(PRODUCT_ID, QTY, ORDER_ID));
        }

        @Test
        @DisplayName("InventoryLockConflictException → order.ReserveConflictException 변환")
        void reserve_lockConflict_mapsToConflictException() {
            willThrow(new InventoryLockConflictException(PRODUCT_ID))
                    .given(inventoryCommandService).reserve(ORDER_ID, PRODUCT_ID, QTY);

            assertThrows(ReserveConflictException.class,
                    () -> sut.reserve(PRODUCT_ID, QTY, ORDER_ID));
        }
    }
}
