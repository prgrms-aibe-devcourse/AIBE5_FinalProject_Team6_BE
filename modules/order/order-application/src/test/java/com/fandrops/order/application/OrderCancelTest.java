package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.exception.OrderCancellationNotAllowedException;
import com.fandrops.order.domain.exception.OrderNotFoundException;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService.cancelOrder() 단위 테스트")
class OrderCancelTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryReservePort inventoryReservePort;
    @Mock private InventoryRestorePort inventoryRestorePort;
    @Mock private AccessTicketValidatePort accessTicketValidatePort;
    @Mock private ProductPricePort productPricePort;

    @InjectMocks
    private OrderService sut;

    private static final Long ORDER_ID  = 1L;
    private static final Long FAN_ID    = 100L;
    private static final Long OTHER_FAN = 999L;
    private static final Long PRODUCT_ID = 10L;

    private Order orderWith(OrderStatus status) {
        List<OrderItem> items = List.of(new OrderItem(PRODUCT_ID, 2, BigDecimal.valueOf(5000)));
        return Order.reconstitute(ORDER_ID, FAN_ID, items, status,
                BigDecimal.valueOf(10000), "opk_test", "idem_test", null);
    }

    @Nested
    @DisplayName("정상 취소")
    class Success {

        @Test
        @DisplayName("RESERVED 상태 — 재고 복구 후 CANCELLED 전이")
        void reserved_restoreAndCancel() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.RESERVED)));

            sut.cancelOrder(ORDER_ID, FAN_ID);

            verify(inventoryRestorePort).restore(PRODUCT_ID, 2, ORDER_ID);
            verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("취소 불가 케이스")
    class NotAllowed {

        @Test
        @DisplayName("존재하지 않는 주문 — OrderNotFoundException")
        void notFound_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.cancelOrder(ORDER_ID, FAN_ID));
            verifyNoInteractions(inventoryRestorePort);
        }

        @Test
        @DisplayName("타인 주문 접근 — OrderNotFoundException (소유자 노출 방지)")
        void otherFan_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.RESERVED)));

            assertThrows(OrderNotFoundException.class, () -> sut.cancelOrder(ORDER_ID, OTHER_FAN));
            verifyNoInteractions(inventoryRestorePort);
        }

        @Test
        @DisplayName("PAID 상태 — OrderCancellationNotAllowedException")
        void paid_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.PAID)));

            assertThrows(OrderCancellationNotAllowedException.class,
                    () -> sut.cancelOrder(ORDER_ID, FAN_ID));
            verifyNoInteractions(inventoryRestorePort);
        }

        @Test
        @DisplayName("COMPLETED 상태 — OrderCancellationNotAllowedException")
        void completed_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.COMPLETED)));

            assertThrows(OrderCancellationNotAllowedException.class,
                    () -> sut.cancelOrder(ORDER_ID, FAN_ID));
            verifyNoInteractions(inventoryRestorePort);
        }

        @Test
        @DisplayName("이미 CANCELLED — OrderCancellationNotAllowedException")
        void alreadyCancelled_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.CANCELLED)));

            assertThrows(OrderCancellationNotAllowedException.class,
                    () -> sut.cancelOrder(ORDER_ID, FAN_ID));
            verifyNoInteractions(inventoryRestorePort);
        }
    }

    @Nested
    @DisplayName("멱등성 — restore는 재고 서비스 멱등 보장")
    class Idempotency {

        @Test
        @DisplayName("RESERVED → restore 호출 후 CANCELLED 업데이트 순서 보장")
        void restoreCalledBeforeStatusUpdate() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(orderWith(OrderStatus.RESERVED)));
            var order = inOrder(inventoryRestorePort, orderRepository);

            sut.cancelOrder(ORDER_ID, FAN_ID);

            order.verify(inventoryRestorePort).restore(PRODUCT_ID, 2, ORDER_ID);
            order.verify(orderRepository).updateStatus(ORDER_ID, OrderStatus.CANCELLED);
        }
    }
}
