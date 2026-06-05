package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.payment.application.payment.PaymentApprovedEvent;
import com.fandrops.payment.application.payment.PaymentFailedEvent;
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
        Order order = orderService.findOrder(orderId);
        if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        // TX 1: RESERVED → FAILED 선(先) 커밋 — 결제 실패 사실 DB에 영구 기록
        orderService.markAsFailed(orderId);
        try {
            order.getItems().forEach(item ->
                    inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), orderId));
            // TX 2: 복구 성공 시 FAILED → CANCELLED
            orderService.markAsCancelled(orderId);
        } catch (Exception e) {
            log.error("재고 복구 실패 — 주문 {} FAILED 상태 유지, 관리자 확인 필요", orderId, e);
            throw e;
        }
    }

    @Async("sagaExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        Long orderId = event.getOrderId();
        Order order = orderService.findOrder(orderId);
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }
        // TX 1: RESERVED → PAID 선(先) 커밋
        orderService.markAsPaid(orderId);
        try {
            order.getItems().forEach(item ->
                    inventoryConfirmPort.confirm(item.getProductId(), item.getQuantity(), orderId));
            // TX 2: 확정 성공 시 PAID → COMPLETED
            orderService.markAsCompleted(orderId);
        } catch (Exception e) {
            log.error("재고 확정 실패 — 주문 {} PAID 상태 유지, 관리자 확인 필요", orderId, e);
            throw e;
        }
    }
}
