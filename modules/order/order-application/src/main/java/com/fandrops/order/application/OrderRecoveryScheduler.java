package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga 후처리 정체 복구 스케줄러.
 * 이벤트 리스너(PaymentEventListener)가 서버 재시작 등으로 미처리된 주문을 1분 주기로 재처리한다.
 * confirm/restore는 InventoryCommandService의 멱등성 가드(이력 exists 체크)에 의해 중복 실행이 안전하다.
 */
@Component
public class OrderRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderRecoveryScheduler.class);

    /** 결제 대기 타임아웃: RESERVED 상태 유지 허용 시간 (분). */
    private static final long RESERVED_TIMEOUT_MINUTES = 30;

    /** PAID/FAILED 정체 허용 시간 (분). */
    private static final long STUCK_THRESHOLD_MINUTES = 5;

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final InventoryConfirmPort inventoryConfirmPort;
    private final InventoryRestorePort inventoryRestorePort;

    public OrderRecoveryScheduler(OrderRepository orderRepository, OrderService orderService,
                                  InventoryConfirmPort inventoryConfirmPort,
                                  InventoryRestorePort inventoryRestorePort) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.inventoryConfirmPort = inventoryConfirmPort;
        this.inventoryRestorePort = inventoryRestorePort;
    }

    /** 결제 대기 타임아웃: RESERVED → FAILED → CANCELLED */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void cancelExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RESERVED_TIMEOUT_MINUTES);
        List<Order> targets = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.RESERVED, cutoff);
        for (Order order : targets) {
            try {
                orderService.markAsFailed(order.getId());
                order.getItems().forEach(item ->
                        inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), order.getId()));
                orderService.markAsCancelled(order.getId());
                log.info("[Recovery] 결제 타임아웃 주문 {} CANCELLED 처리 완료", order.getId());
            } catch (Exception e) {
                log.error("[Recovery] 결제 타임아웃 복구 실패 — 주문 {}", order.getId(), e);
            }
        }
    }

    /** 후처리 정체 재처리: PAID → (재고 확정) → COMPLETED */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckPaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<Order> targets = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PAID, cutoff);
        for (Order order : targets) {
            try {
                order.getItems().forEach(item ->
                        inventoryConfirmPort.confirm(item.getProductId(), item.getQuantity(), order.getId()));
                orderService.markAsCompleted(order.getId());
                log.info("[Recovery] PAID 정체 주문 {} COMPLETED 처리 완료", order.getId());
            } catch (Exception e) {
                log.error("[Recovery] PAID 정체 복구 실패 — 주문 {}", order.getId(), e);
            }
        }
    }

    /** 보상 정체 재처리: FAILED → (재고 복구) → CANCELLED */
    @Transactional
    @Scheduled(fixedDelay = 60_000)
    public void recoverStuckFailedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<Order> targets = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.FAILED, cutoff);
        for (Order order : targets) {
            try {
                order.getItems().forEach(item ->
                        inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), order.getId()));
                orderService.markAsCancelled(order.getId());
                log.info("[Recovery] FAILED 정체 주문 {} CANCELLED 처리 완료", order.getId());
            } catch (Exception e) {
                log.error("[Recovery] FAILED 정체 복구 실패 — 주문 {}", order.getId(), e);
            }
        }
    }
}
