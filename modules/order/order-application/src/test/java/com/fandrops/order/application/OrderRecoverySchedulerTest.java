package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderRecoveryScheduler 단위 테스트")
class OrderRecoverySchedulerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private InventoryConfirmPort inventoryConfirmPort;
    @Mock private InventoryRestorePort inventoryRestorePort;

    @InjectMocks
    private OrderRecoveryScheduler sut;

    private static final Long ORDER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;

    private Order orderWith(OrderStatus status, List<OrderItem> items) {
        return Order.reconstitute(ORDER_ID, 1L, items, status,
                BigDecimal.valueOf(10000), "opk_test", "idem_test");
    }

    @Nested
    @DisplayName("cancelExpiredReservations() — RESERVED 타임아웃")
    class CancelExpiredReservations {

        @Test
        @DisplayName("RESERVED 정체 주문 — FAILED 전이 → 재고 복구 → CANCELLED 전이")
        void reserved_timeout_cancelledWithRestore() {
            OrderItem item = new OrderItem(PRODUCT_ID, 2, BigDecimal.valueOf(5000));
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.RESERVED), any()))
                    .willReturn(List.of(orderWith(OrderStatus.RESERVED, List.of(item))));

            sut.cancelExpiredReservations();

            verify(orderService).markAsFailed(ORDER_ID);
            verify(inventoryRestorePort).restore(PRODUCT_ID, 2, ORDER_ID);
            verify(orderService).markAsCancelled(ORDER_ID);
        }

        @Test
        @DisplayName("대상 주문 없으면 아무 작업도 하지 않는다")
        void reserved_noTargets_noAction() {
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.RESERVED), any()))
                    .willReturn(List.of());

            sut.cancelExpiredReservations();

            verify(orderService, never()).markAsFailed(any());
        }

        @Test
        @DisplayName("예외 발생 시 나머지 주문 처리를 계속한다")
        void reserved_exceptionOnOneOrder_continuesOthers() {
            Long orderId2 = 2L;
            Order failingOrder = orderWith(OrderStatus.RESERVED, List.of());
            Order normalOrder = Order.reconstitute(orderId2, 1L, List.of(), OrderStatus.RESERVED,
                    BigDecimal.valueOf(5000), "opk_2", "idem_2");

            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.RESERVED), any()))
                    .willReturn(List.of(failingOrder, normalOrder));
            doThrow(new RuntimeException("restore 실패")).when(orderService).markAsFailed(ORDER_ID);

            sut.cancelExpiredReservations();

            verify(orderService).markAsFailed(orderId2);
        }
    }

    @Nested
    @DisplayName("recoverStuckPaidOrders() — PAID 정체 복구")
    class RecoverStuckPaidOrders {

        @Test
        @DisplayName("PAID 정체 주문 — 재고 확정 → COMPLETED 전이")
        void paid_stuck_completedWithConfirm() {
            OrderItem item = new OrderItem(PRODUCT_ID, 1, BigDecimal.valueOf(10000));
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PAID), any()))
                    .willReturn(List.of(orderWith(OrderStatus.PAID, List.of(item))));

            sut.recoverStuckPaidOrders();

            verify(inventoryConfirmPort).confirm(PRODUCT_ID, 1, ORDER_ID);
            verify(orderService).markAsCompleted(ORDER_ID);
        }

        @Test
        @DisplayName("대상 주문 없으면 아무 작업도 하지 않는다")
        void paid_noTargets_noAction() {
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.PAID), any()))
                    .willReturn(List.of());

            sut.recoverStuckPaidOrders();

            verify(orderService, never()).markAsCompleted(any());
        }
    }

    @Nested
    @DisplayName("recoverStuckFailedOrders() — FAILED 정체 복구")
    class RecoverStuckFailedOrders {

        @Test
        @DisplayName("FAILED 정체 주문 — 재고 복구 → CANCELLED 전이")
        void failed_stuck_cancelledWithRestore() {
            OrderItem item = new OrderItem(PRODUCT_ID, 3, BigDecimal.valueOf(3000));
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.FAILED), any()))
                    .willReturn(List.of(orderWith(OrderStatus.FAILED, List.of(item))));

            sut.recoverStuckFailedOrders();

            verify(inventoryRestorePort).restore(PRODUCT_ID, 3, ORDER_ID);
            verify(orderService).markAsCancelled(ORDER_ID);
        }

        @Test
        @DisplayName("대상 주문 없으면 아무 작업도 하지 않는다")
        void failed_noTargets_noAction() {
            given(orderRepository.findByStatusAndUpdatedAtBefore(eq(OrderStatus.FAILED), any()))
                    .willReturn(List.of());

            sut.recoverStuckFailedOrders();

            verify(orderService, never()).markAsCancelled(any());
        }
    }
}
