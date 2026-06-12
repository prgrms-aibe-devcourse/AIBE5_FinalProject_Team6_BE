package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener 단위 테스트")
class PaymentEventListenerTest {

    @Mock private OrderService orderService;
    @Mock private InventoryRestorePort inventoryRestorePort;
    @Mock private InventoryConfirmPort inventoryConfirmPort;

    @InjectMocks
    private PaymentEventListener sut;

    private static final Long ORDER_ID = 1L;

    private Order orderWithStatus(OrderStatus status) {
        return Order.reconstitute(ORDER_ID, 1L, List.of(), status,
                BigDecimal.valueOf(10000), "opk_test", "idem_test", null);
    }

    @Nested
    @DisplayName("handlePaymentFailed()")
    class HandlePaymentFailed {

        @Test
        @DisplayName("RESERVED 상태 — 정상 보상 흐름 실행")
        void failed_reserved_executesCompensation() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.RESERVED));

            sut.handlePaymentFailed(new PaymentFailedEvent(ORDER_ID));

            verify(orderService).markAsFailed(ORDER_ID);
        }

        @Test
        @DisplayName("이미 FAILED — 즉시 스킵, markAsFailed 미호출")
        void failed_alreadyFailed_skips() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.FAILED));

            sut.handlePaymentFailed(new PaymentFailedEvent(ORDER_ID));

            verify(orderService, never()).markAsFailed(any());
            verify(inventoryRestorePort, never()).restore(any(), anyInt(), any());
        }

        @Test
        @DisplayName("이미 CANCELLED — 즉시 스킵, markAsFailed 미호출")
        void failed_alreadyCancelled_skips() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.CANCELLED));

            sut.handlePaymentFailed(new PaymentFailedEvent(ORDER_ID));

            verify(orderService, never()).markAsFailed(any());
            verify(inventoryRestorePort, never()).restore(any(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("handlePaymentApproved()")
    class HandlePaymentApproved {

        @Test
        @DisplayName("RESERVED 상태 — 정상 확정 흐름 실행")
        void approved_reserved_executesConfirmation() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.RESERVED));

            sut.handlePaymentApproved(new PaymentApprovedEvent(ORDER_ID));

            verify(orderService).markAsPaid(ORDER_ID);
        }

        @Test
        @DisplayName("이미 PAID — 즉시 스킵, markAsPaid 미호출")
        void approved_alreadyPaid_skips() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.PAID));

            sut.handlePaymentApproved(new PaymentApprovedEvent(ORDER_ID));

            verify(orderService, never()).markAsPaid(any());
            verify(inventoryConfirmPort, never()).confirm(any(), anyInt(), any());
        }

        @Test
        @DisplayName("이미 COMPLETED — 즉시 스킵, markAsPaid 미호출")
        void approved_alreadyCompleted_skips() {
            given(orderService.findOrder(ORDER_ID)).willReturn(orderWithStatus(OrderStatus.COMPLETED));

            sut.handlePaymentApproved(new PaymentApprovedEvent(ORDER_ID));

            verify(orderService, never()).markAsPaid(any());
            verify(inventoryConfirmPort, never()).confirm(any(), anyInt(), any());
        }
    }
}
