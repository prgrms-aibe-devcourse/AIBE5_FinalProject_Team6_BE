package com.fandrops.inventory.domain;

import com.fandrops.inventory.domain.exception.InvalidInventoryStateException;
import com.fandrops.inventory.domain.exception.OutOfStockException;
import com.fandrops.inventory.domain.exception.ReserveFailedException;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Inventory 도메인 단위 테스트")
class InventoryTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long RESTOCK_ID = 20L;

    @Nested
    @DisplayName("InventoryHistory.of() — changedAt 주입")
    class InventoryHistoryOf {

        @Test
        @DisplayName("changedAt 오버로드는 주입된 시각을 그대로 사용")
        void of_withFixedTime() {
            LocalDateTime fixed = LocalDateTime.of(2024, 1, 15, 10, 0, 0);
            InventoryHistory history = InventoryHistory.of(
                    1L, InventoryChangeType.RESERVE, 10, 100, 90, ORDER_ID, InventoryRefType.ORDER, fixed);

            assertEquals(fixed, history.getChangedAt());
        }

        @Test
        @DisplayName("changedAt 없는 오버로드는 현재 시각을 사용")
        void of_withoutTime_usesNow() {
            LocalDateTime before = LocalDateTime.now();
            InventoryHistory history = InventoryHistory.of(
                    1L, InventoryChangeType.RESERVE, 10, 100, 90, ORDER_ID, InventoryRefType.ORDER);
            LocalDateTime after = LocalDateTime.now();

            assertFalse(history.getChangedAt().isBefore(before));
            assertFalse(history.getChangedAt().isAfter(after));
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("availableQty == totalQty - reservedQty 이면 정상 생성")
        void reconstitute_validInvariant() {
            assertDoesNotThrow(() -> Inventory.reconstitute(1L, PRODUCT_ID, 100, 30, 70, 0));
        }

        @Test
        @DisplayName("availableQty != totalQty - reservedQty 이면 InvalidInventoryStateException")
        void reconstitute_brokenInvariant() {
            assertThrows(InvalidInventoryStateException.class,
                () -> Inventory.reconstitute(1L, PRODUCT_ID, 100, 50, 99, 0));
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("신규 생성 시 reservedQty=0, availableQty=initialQty")
        void create_initialState() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);

            assertEquals(100, inventory.getTotalQty());
            assertEquals(0, inventory.getReservedQty());
            assertEquals(100, inventory.getAvailableQty());
        }

        @Test
        @DisplayName("신규 생성 시 id는 null")
        void create_idIsNull() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);

            assertNull(inventory.getId());
        }

        @Test
        @DisplayName("initialQty <= 0 이면 InvalidInventoryStateException")
        void create_negativeOrZeroQty() {
            assertThrows(InvalidInventoryStateException.class, () -> Inventory.create(PRODUCT_ID, 0));
            assertThrows(InvalidInventoryStateException.class, () -> Inventory.create(PRODUCT_ID, -1));
        }
    }

    @Nested
    @DisplayName("reserve()")
    class Reserve {

        private Inventory inventory;

        @BeforeEach
        void setUp() {
            inventory = Inventory.create(PRODUCT_ID, 100);
        }

        @Test
        @DisplayName("정상 예약 시 reservedQty 증가, availableQty 감소")
        void reserve_success() {
            inventory.reserve(20, ORDER_ID);

            assertEquals(20, inventory.getReservedQty());
            assertEquals(80, inventory.getAvailableQty());
            assertEquals(100, inventory.getTotalQty());
        }

        @Test
        @DisplayName("정상 예약 시 I-2 불변식 유지(availableQty = totalQty - reservedQty)")
        void reserve_invariantI2() {
            inventory.reserve(20, ORDER_ID);
            assertEquals(inventory.getTotalQty() - inventory.getReservedQty(), inventory.getAvailableQty());
        }

        @Test
        @DisplayName("정상 예약 시 RESERVE 이력 반환")
        void reserve_returnHistory() {
            InventoryHistory history = inventory.reserve(20, ORDER_ID);

            assertEquals(InventoryChangeType.RESERVE, history.getChangeType());
            assertEquals(20, history.getDeltaQty());
            assertEquals(100, history.getQtyBefore());
            assertEquals(80, history.getQtyAfter());
            assertEquals(ORDER_ID, history.getReferenceId());
            assertEquals(InventoryRefType.ORDER, history.getRefType());
            assertNotNull(history.getChangedAt());
            assertNull(history.getId());
        }

        @Test
        @DisplayName("availableQty == 0 이면 OutOfStockException")
        void reserve_outOfStock() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 5);
            inventory.reserve(5, ORDER_ID);

            assertThrows(OutOfStockException.class, () -> inventory.reserve(1, ORDER_ID));
        }

        @Test
        @DisplayName("availableQty > 0 but < qty 이면 ReserveFailedException")
        void reserve_reserveFailed() {
            assertThrows(ReserveFailedException.class, () -> inventory.reserve(101, ORDER_ID));
        }

        @Test
        @DisplayName("availableQty == qty 이면 정상 예약 성공 (경계값)")
        void reserve_exactAvailableQty() {
            assertDoesNotThrow(() -> inventory.reserve(100, ORDER_ID));
            assertEquals(0, inventory.getAvailableQty());
        }

        @Test
        @DisplayName("qty <= 0 이면 InvalidInventoryStateException")
        void reserve_negativeOrZeroQty() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.reserve(0, ORDER_ID));
            assertThrows(InvalidInventoryStateException.class, () -> inventory.reserve(-1, ORDER_ID));
        }
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        private Inventory inventory;

        @BeforeEach
        void setUp() {
            inventory = Inventory.create(PRODUCT_ID, 100);
            inventory.reserve(20, ORDER_ID);
        }

        @Test
        @DisplayName("정상 확정 시 totalQty, reservedQty 감소 / availableQty 유지")
        void confirm_success() {
            inventory.confirm(20, ORDER_ID);

            assertEquals(80, inventory.getTotalQty());
            assertEquals(0, inventory.getReservedQty());
            assertEquals(80, inventory.getAvailableQty());

            assertEquals(inventory.getTotalQty() - inventory.getReservedQty(),
                    inventory.getAvailableQty());
        }

        @Test
        @DisplayName("정상 확정 시 DECREASE 이력 반환")
        void confirm_returnHistory() {
            InventoryHistory history = inventory.confirm(20, ORDER_ID);

            assertEquals(InventoryChangeType.DECREASE, history.getChangeType());
            assertEquals(20, history.getDeltaQty());
            assertEquals(100, history.getQtyBefore());
            assertEquals(80, history.getQtyAfter());
            assertEquals(ORDER_ID, history.getReferenceId());
            assertEquals(InventoryRefType.ORDER, history.getRefType());
        }

        @Test
        @DisplayName("reservedQty < qty 이면 InvalidInventoryStateException")
        void confirm_invalidState() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.confirm(21, ORDER_ID));
        }

        @Test
        @DisplayName("qty <= 0 이면 InvalidInventoryStateException")
        void confirm_negativeOrZeroQty() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.confirm(0, ORDER_ID));
            assertThrows(InvalidInventoryStateException.class, () -> inventory.confirm(-1, ORDER_ID));
        }
    }

    @Nested
    @DisplayName("restore()")
    class Restore {

        private Inventory inventory;

        @BeforeEach
        void setUp() {
            inventory = Inventory.create(PRODUCT_ID, 100);
            inventory.reserve(30, ORDER_ID);
        }

        @Test
        @DisplayName("정상 복원 시 reservedQty 감소, availableQty 증가")
        void restore_success() {
            inventory.restore(30, ORDER_ID);

            assertEquals(0, inventory.getReservedQty());
            assertEquals(100, inventory.getAvailableQty());
            assertEquals(100, inventory.getTotalQty());
        }

        @Test
        @DisplayName("정상 복원 시 RELEASE 이력 반환")
        void restore_returnHistory() {
            InventoryHistory history = inventory.restore(30, ORDER_ID);

            assertEquals(InventoryChangeType.RELEASE, history.getChangeType());
            assertEquals(30, history.getDeltaQty());
            assertEquals(70, history.getQtyBefore());
            assertEquals(100, history.getQtyAfter());
            assertEquals(ORDER_ID, history.getReferenceId());
            assertEquals(InventoryRefType.ORDER, history.getRefType());
            assertNotNull(history.getChangedAt());
        }

        @Test
        @DisplayName("정상 복원 시 I-2 불변식 유지")
        void restore_invariantI2() {
            inventory.restore(30, ORDER_ID);

            assertEquals(
                    inventory.getTotalQty() - inventory.getReservedQty(),
                    inventory.getAvailableQty()
            );
        }

        @Test
        @DisplayName("reservedQty < qty 이면 InvalidInventoryStateException")
        void restore_invalidState() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.restore(31, ORDER_ID));
        }

        @Test
        @DisplayName("qty <= 0 이면 InvalidInventoryStateException")
        void restore_negativeOrZeroQty() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.restore(0, ORDER_ID));
            assertThrows(InvalidInventoryStateException.class, () -> inventory.restore(-1, ORDER_ID));
        }
    }

    @Nested
    @DisplayName("increase()")
    class Increase {

        private Inventory inventory;

        @BeforeEach
        void setUp() {
            inventory = Inventory.create(PRODUCT_ID, 100);
        }

        @Test
        @DisplayName("재입고 시 totalQty, availableQty 증가")
        void increase_success() {
            inventory.increase(50, RESTOCK_ID);

            assertEquals(150, inventory.getTotalQty());
            assertEquals(150, inventory.getAvailableQty());
            assertEquals(0, inventory.getReservedQty());
        }

        @Test
        @DisplayName("재입고 시 I-2 불변식 유지")
        void increase_invariantI2() {
            inventory.increase(50, RESTOCK_ID);

            assertEquals(
                    inventory.getTotalQty() - inventory.getReservedQty(),
                    inventory.getAvailableQty()
            );
        }

        @Test
        @DisplayName("재입고 시 INCREASE 이력 반환")
        void increase_returnsHistory() {
            InventoryHistory history = inventory.increase(50, RESTOCK_ID);

            assertEquals(InventoryChangeType.INCREASE, history.getChangeType());
            assertEquals(50, history.getDeltaQty());
            assertEquals(100, history.getQtyBefore());
            assertEquals(150, history.getQtyAfter());
            assertEquals(RESTOCK_ID, history.getReferenceId());
            assertEquals(InventoryRefType.RESTOCK, history.getRefType());
        }

        @Test
        @DisplayName("qty <= 0 이면 InvalidInventoryStateException")
        void increase_negativeOrZeroQty() {
            assertThrows(InvalidInventoryStateException.class, () -> inventory.increase(0, RESTOCK_ID));
            assertThrows(InvalidInventoryStateException.class, () -> inventory.increase(-1, RESTOCK_ID));
        }
    }

    @Nested
    @DisplayName("isSoldOut()")
    class IsSoldOut {

        @Test
        @DisplayName("availableQty == 0 이면 true")
        void isSoldOut_true() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);
            inventory.reserve(10, ORDER_ID);

            assertTrue(inventory.isSoldOut());
        }

        @Test
        @DisplayName("availableQty > 0 이면 false")
        void isSoldOut_false() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 10);

            assertFalse(inventory.isSoldOut());
        }
    }

    @Nested
    @DisplayName("복합 시나리오")
    class Scenario {

        @Test
        @DisplayName("reserve → confirm → restore 후 I-2 불변식 유지")
        void scenario_reserveConfirmRestore() {
            Inventory inventory = Inventory.create(PRODUCT_ID, 100);

            inventory.reserve(20, ORDER_ID);
            inventory.reserve(10, ORDER_ID);
            inventory.confirm(20, ORDER_ID);
            inventory.restore(10, ORDER_ID);

            assertEquals(
                    inventory.getTotalQty() - inventory.getReservedQty(),
                    inventory.getAvailableQty()
            );
            assertEquals(80, inventory.getTotalQty());
            assertEquals(0, inventory.getReservedQty());
            assertEquals(80, inventory.getAvailableQty());
        }
    }
}
