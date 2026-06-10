package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderService orderService;
    private final InventoryRestorePort inventoryRestorePort;
    private final InventoryConfirmPort inventoryConfirmPort;

    public PaymentEventListener(OrderService orderService,
                                InventoryRestorePort inventoryRestorePort,
                                InventoryConfirmPort inventoryConfirmPort) {
        this.orderService = orderService;
        this.inventoryRestorePort = inventoryRestorePort;
        this.inventoryConfirmPort = inventoryConfirmPort;
    }

    @Async("sagaExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Long orderId = event.getOrderId();
        // 멱등 가드: 이미 FAILED/CANCELLED이면 재고 복구 없이 즉시 스킵 — 중복 이벤트 에러 로그 오발행 방지
        Order order = orderService.findOrder(orderId);
        if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            log.info("주문 {}가 이미 FAILED/CANCELLED 상태이므로 이벤트를 스킵합니다.", orderId);
            return;
        }
        orderService.markAsFailed(orderId);
        executeInventoryStep(
                orderId,
                item -> inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), orderId),
                () -> orderService.markAsCancelled(orderId),
                "재고 복구 실패 — 주문 {} FAILED 상태 유지, 관리자 확인 필요");
    }

    @Async("sagaExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        Long orderId = event.getOrderId();
        // 멱등 가드: 이미 PAID/COMPLETED이면 재고 확정 없이 즉시 스킵 — 중복 이벤트 에러 로그 오발행 방지
        Order order = orderService.findOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.COMPLETED) {
            log.info("주문 {}가 이미 PAID/COMPLETED 상태이므로 이벤트를 스킵합니다.", orderId);
            return;
        }
        orderService.markAsPaid(orderId);
        executeInventoryStep(
                orderId,
                item -> inventoryConfirmPort.confirm(item.getProductId(), item.getQuantity(), orderId),
                () -> orderService.markAsCompleted(orderId),
                "재고 확정 실패 — 주문 {} PAID 상태 유지, 관리자 확인 필요");
    }

    // 재고 작업 공통 패턴: 상태 전이 후 items 재조회 → 재고 액션 → 최종 상태 전이
    private void executeInventoryStep(Long orderId, Consumer<OrderItem> inventoryAction,
                                      Runnable finalStep, String errorMessage) {
        try {
            Order order = orderService.findOrder(orderId);
            order.getItems().forEach(inventoryAction);
            finalStep.run();
        } catch (Exception e) {
            log.error(errorMessage, orderId, e);
            throw e;
        }
    }
}
