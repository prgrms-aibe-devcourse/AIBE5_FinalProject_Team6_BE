package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
        orderService.markAsFailed(orderId);  // 내부 가드: FAILED/CANCELLED면 no-op
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
        orderService.markAsPaid(orderId);  // 내부 가드: PAID/COMPLETED면 no-op
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
