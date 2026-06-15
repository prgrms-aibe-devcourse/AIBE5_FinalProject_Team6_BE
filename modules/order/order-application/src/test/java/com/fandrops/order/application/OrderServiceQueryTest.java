package com.fandrops.order.application;

import com.fandrops.order.application.dto.OrderDetailResponse;
import com.fandrops.order.application.dto.OrderListResponse;
import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 조회 단위 테스트")
class OrderServiceQueryTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryReservePort inventoryReservePort;
    @Mock private InventoryRestorePort inventoryRestorePort;
    @Mock private AccessTicketValidatePort accessTicketValidatePort;
    @Mock private ProductPricePort productPricePort;

    @InjectMocks
    private OrderService sut;

    private static final Long ORDER_ID = 1L;
    private static final Long FAN_ID   = 100L;
    private static final Long OTHER_FAN_ID = 999L;

    private Order order(OrderStatus status) {
        List<OrderItem> items = List.of(new OrderItem(10L, 2, BigDecimal.valueOf(5000)));
        return Order.reconstitute(ORDER_ID, FAN_ID, items, status,
                BigDecimal.valueOf(10000), "opk_test", "idem_test",
                LocalDateTime.of(2026, 6, 12, 10, 0, 0));
    }

    @Nested
    @DisplayName("getOrderDetail()")
    class GetOrderDetail {

        @Test
        @DisplayName("본인 주문 조회 — OrderDetailResponse 반환")
        void success_returnDetail() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order(OrderStatus.RESERVED)));

            OrderDetailResponse result = sut.getOrderDetail(ORDER_ID, FAN_ID);

            assertEquals(ORDER_ID, result.getOrderId());
            assertEquals("RESERVED", result.getStatus());
            assertEquals(0, BigDecimal.valueOf(10000).compareTo(result.getTotalAmount()));
            assertEquals(1, result.getItems().size());
            assertEquals(2, result.getItems().get(0).getQuantity());
            assertEquals(LocalDateTime.of(2026, 6, 12, 10, 0, 0), result.getCreatedAt());
        }

        @Test
        @DisplayName("존재하지 않는 주문 — OrderNotFoundException")
        void notFound_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            assertThrows(OrderNotFoundException.class, () -> sut.getOrderDetail(ORDER_ID, FAN_ID));
        }

        @Test
        @DisplayName("타인 주문 접근 — OrderNotFoundException (소유자 검증)")
        void otherFan_throws() {
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order(OrderStatus.RESERVED)));

            assertThrows(OrderNotFoundException.class, () -> sut.getOrderDetail(ORDER_ID, OTHER_FAN_ID));
        }
    }

    @Nested
    @DisplayName("getMyOrders()")
    class GetMyOrders {

        @Test
        @DisplayName("첫 페이지 — cursor null, size 미만이면 nextCursor null")
        void firstPage_noNextCursor() {
            given(orderRepository.findByFanId(FAN_ID, null, 20))
                    .willReturn(List.of(order(OrderStatus.COMPLETED)));

            OrderListResponse result = sut.getMyOrders(FAN_ID, null, 20);

            assertEquals(1, result.getItems().size());
            assertNull(result.getNextCursor());
        }

        @Test
        @DisplayName("size만큼 채워지면 nextCursor = 마지막 주문 id")
        void fullPage_nextCursorSet() {
            Order o1 = Order.reconstitute(3L, FAN_ID, List.of(), OrderStatus.COMPLETED,
                    BigDecimal.valueOf(10000), "opk1", "idem1", null);
            Order o2 = Order.reconstitute(2L, FAN_ID, List.of(), OrderStatus.COMPLETED,
                    BigDecimal.valueOf(10000), "opk2", "idem2", null);
            Order o3 = Order.reconstitute(1L, FAN_ID, List.of(), OrderStatus.COMPLETED,
                    BigDecimal.valueOf(10000), "opk3", "idem3", null);
            given(orderRepository.findByFanId(FAN_ID, null, 3)).willReturn(List.of(o1, o2, o3));

            OrderListResponse result = sut.getMyOrders(FAN_ID, null, 3);

            assertEquals(3, result.getItems().size());
            assertEquals(1L, result.getNextCursor());
        }

        @Test
        @DisplayName("결과 없으면 빈 목록 반환")
        void noOrders_emptyList() {
            given(orderRepository.findByFanId(FAN_ID, null, 20)).willReturn(List.of());

            OrderListResponse result = sut.getMyOrders(FAN_ID, null, 20);

            assertTrue(result.getItems().isEmpty());
            assertNull(result.getNextCursor());
        }
    }
}
