package com.fandrops.inventory.application;

import com.fandrops.inventory.application.exception.InventoryNotFoundException;
import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCommandService 단위 테스트")
class InventoryCommandServiceTest {

    @Mock
    private InventoryReadRepository inventoryReadRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;

    @Mock
    private InventoryReserveTxHelper reserveTxHelper;

    @InjectMocks
    private InventoryCommandService sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long RESTOCK_ID = 20L;

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        @Test
        @DisplayName("성공 시 reserveTxHelper.reserveOnce() 1회 호출")
        void reserve_success_callsReserveOnce() {
            sut.reserve(ORDER_ID, PRODUCT_ID, 10);

            verify(reserveTxHelper).reserveOnce(ORDER_ID, PRODUCT_ID, 10);
        }

        @Test
        @DisplayName("OutOfStockException 발생 시 즉시 전파")
        void reserve_outOfStock_propagates() {
            doThrow(new OutOfStockException(PRODUCT_ID)).when(reserveTxHelper).reserveOnce(ORDER_ID, PRODUCT_ID, 10);

            assertThrows(OutOfStockException.class, () -> sut.reserve(ORDER_ID, PRODUCT_ID, 10));

            verify(reserveTxHelper, times(1)).reserveOnce(ORDER_ID, PRODUCT_ID, 10);
        }

        @Test
        @DisplayName("InventoryNotFoundException 발생 시 즉시 전파")
        void reserve_inventoryNotFound_propagates() {
            doThrow(new InventoryNotFoundException(PRODUCT_ID)).when(reserveTxHelper).reserveOnce(ORDER_ID, PRODUCT_ID, 10);

            assertThrows(InventoryNotFoundException.class, () -> sut.reserve(ORDER_ID, PRODUCT_ID, 10));

            verify(reserveTxHelper, times(1)).reserveOnce(ORDER_ID, PRODUCT_ID, 10);
        }
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("DECREASE 이력 이미 존재하면 early-return — confirmAtomic·save 미호출 (멱등성)")
        void confirm_alreadyProcessed_earlyReturn() {
            given(inventoryHistoryRepository.existsByReferenceIdAndRefTypeAndChangeType(
                    ORDER_ID, InventoryRefType.ORDER, InventoryChangeType.DECREASE)).willReturn(true);

            sut.confirm(ORDER_ID, PRODUCT_ID, 20);

            verify(inventoryRepository, never()).confirmAtomic(anyLong(), anyInt());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 확정 시 DECREASE 이력 저장, save() 미호출")
        void confirm_savesHistoryWithoutDirectSave() {
            // post-update 상태: totalQty=80, reservedQty=0, availableQty=80 (20 confirm 후)
            Inventory postUpdate = Inventory.reconstitute(1L, PRODUCT_ID, 80, 0, 80, 0);
            given(inventoryRepository.confirmAtomic(PRODUCT_ID, 20)).willReturn(1);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(postUpdate));

            sut.confirm(ORDER_ID, PRODUCT_ID, 20);

            verify(inventoryRepository, never()).save(any());
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            InventoryHistory history = captor.getValue();
            assertEquals(InventoryChangeType.DECREASE, history.getChangeType());
            assertEquals(ORDER_ID, history.getReferenceId());
            assertEquals(20, history.getDeltaQty());
            assertEquals(100, history.getQtyBefore());  // qtyAfter(80) + qty(20)
            assertEquals(80, history.getQtyAfter());
        }

        @Test
        @DisplayName("reservedQty 부족 시 InvalidInventoryStateException 전파, save 미호출")
        void confirm_insufficientReserved_propagatesWithoutSave() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);  // reservedQty=0
            given(inventoryRepository.confirmAtomic(PRODUCT_ID, 10)).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(InvalidInventoryStateException.class,
                    () -> sut.confirm(ORDER_ID, PRODUCT_ID, 10));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void confirm_inventoryNotFound() {
            given(inventoryRepository.confirmAtomic(PRODUCT_ID, 20)).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.confirm(ORDER_ID, PRODUCT_ID, 20));
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        @Test
        @DisplayName("RELEASE 이력 이미 존재하면 early-return — restoreAtomic·save 미호출 (멱등성)")
        void restore_alreadyProcessed_earlyReturn() {
            given(inventoryHistoryRepository.existsByReferenceIdAndRefTypeAndChangeType(
                    ORDER_ID, InventoryRefType.ORDER, InventoryChangeType.RELEASE)).willReturn(true);

            sut.restore(ORDER_ID, PRODUCT_ID, 30);

            verify(inventoryRepository, never()).restoreAtomic(anyLong(), anyInt());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 복원 시 RELEASE 이력 저장, save() 미호출")
        void restore_savesHistoryWithoutDirectSave() {
            // post-update 상태: reservedQty=0, availableQty=100 (30 restore 후)
            Inventory postUpdate = Inventory.reconstitute(1L, PRODUCT_ID, 100, 0, 100, 0);
            given(inventoryRepository.restoreAtomic(PRODUCT_ID, 30)).willReturn(1);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(postUpdate));

            sut.restore(ORDER_ID, PRODUCT_ID, 30);

            verify(inventoryRepository, never()).save(any());
            ArgumentCaptor<InventoryHistory> captor = ArgumentCaptor.forClass(InventoryHistory.class);
            verify(inventoryHistoryRepository).save(captor.capture());
            InventoryHistory history = captor.getValue();
            assertEquals(InventoryChangeType.RELEASE, history.getChangeType());
            assertEquals(ORDER_ID, history.getReferenceId());
            assertEquals(30, history.getDeltaQty());
            assertEquals(70, history.getQtyBefore());   // qtyAfter(100) - qty(30)
            assertEquals(100, history.getQtyAfter());
        }

        @Test
        @DisplayName("reservedQty 부족 시 InvalidInventoryStateException 전파, save 미호출")
        void restore_insufficientReserved_propagatesWithoutSave() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);  // reservedQty=0
            given(inventoryRepository.restoreAtomic(PRODUCT_ID, 10)).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

            assertThrows(InvalidInventoryStateException.class,
                    () -> sut.restore(ORDER_ID, PRODUCT_ID, 10));

            verify(inventoryRepository, never()).save(any());
            verify(inventoryHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("재고 없는 상품이면 InventoryNotFoundException")
        void restore_inventoryNotFound() {
            given(inventoryRepository.restoreAtomic(PRODUCT_ID, 30)).willReturn(0);
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

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
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.of(inventory));

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
            given(inventoryReadRepository.findByProductId(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(InventoryNotFoundException.class,
                    () -> sut.increase(RESTOCK_ID, PRODUCT_ID, 50));
        }
    }
}
