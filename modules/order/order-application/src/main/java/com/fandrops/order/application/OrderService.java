package com.fandrops.order.application;

import com.fandrops.order.application.dto.AgencyOrderListItemResponse;
import com.fandrops.order.application.dto.AgencyOrderListResponse;
import com.fandrops.order.application.dto.CreateOrderCommand;
import com.fandrops.order.application.dto.CreateOrderResult;
import com.fandrops.order.application.dto.OrderDetailResponse;
import com.fandrops.order.application.dto.OrderListItemResponse;
import com.fandrops.order.application.dto.OrderListResponse;
import com.fandrops.order.domain.AgencyOrderSummary;
import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.exception.OrderCancellationNotAllowedException;
import com.fandrops.order.domain.exception.OrderNotFoundException;
import com.fandrops.order.domain.exception.OutOfStockException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 유스케이스.
 * AccessTicket 검증 → 주문 PENDING 저장(짧은 TX) → 재고 예약 retry → RESERVED/CANCELLED(짧은 TX).
 * outer long TX를 제거해 재고 예약 retry 중 HikariCP 커넥션을 점유하지 않는다.
 */
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderCreateTxHelper orderCreateTxHelper;
    private final InventoryReservePort inventoryReservePort;
    private final InventoryRestorePort inventoryRestorePort;
    private final AccessTicketValidatePort accessTicketValidatePort;
    private final ProductPricePort productPricePort;

    public OrderService(OrderRepository orderRepository,
                        OrderCreateTxHelper orderCreateTxHelper,
                        InventoryReservePort inventoryReservePort,
                        InventoryRestorePort inventoryRestorePort,
                        AccessTicketValidatePort accessTicketValidatePort,
                        ProductPricePort productPricePort) {
        this.orderRepository = orderRepository;
        this.orderCreateTxHelper = orderCreateTxHelper;
        this.inventoryReservePort = inventoryReservePort;
        this.inventoryRestorePort = inventoryRestorePort;
        this.accessTicketValidatePort = accessTicketValidatePort;
        this.productPricePort = productPricePort;
    }

    public CreateOrderResult createOrder(CreateOrderCommand command) {
        // 1. accessTicket 검증 — null이면 상시 판매로 간주하고 스킵 (TODO: 형성빈 협의 후 정식 처리)
        Long primaryProductId = command.getItems().get(0).getProductId();
        if (command.getAccessTicket() != null) {
            accessTicketValidatePort.validate(command.getAccessTicket(), command.getFanId(), primaryProductId);
        }

        // 2. 상품 가격 조회 후 OrderItem 생성
        List<OrderItem> items = command.getItems().stream()
                .map(cmd -> {
                    BigDecimal price = productPricePort.getPrice(cmd.getProductId());
                    return new OrderItem(cmd.getProductId(), cmd.getQuantity(), price);
                })
                .toList();

        // 3. 주문 PENDING 저장 — 짧은 TX로 커밋 후 커넥션 반환
        Order order = Order.create(command.getFanId(), items);
        Order saved = orderCreateTxHelper.savePendingOrder(order);

        // 4. 재고 예약 retry — TX 없이 실행, reserveOnce(REQUIRED)가 각 시도마다 독립 TX
        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : saved.getItems()) {
                inventoryReservePort.reserve(item.getProductId(), item.getQuantity(), saved.getId());
                reserved.add(item);
            }
            // 5. RESERVED 상태 업데이트 — 짧은 TX
            orderCreateTxHelper.markReserved(saved.getId());
            return new CreateOrderResult(saved.getId(), OrderStatus.RESERVED.name(), saved.getOrderPaymentKey());
        } catch (OutOfStockException | ReserveConflictException e) {
            for (OrderItem item : reserved) {
                inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), saved.getId());
            }
            // 6. CANCELLED 상태 업데이트 — 짧은 TX
            orderCreateTxHelper.markCancelled(saved.getId());
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetail(Long orderId, Long fanId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getFanId().equals(fanId)) {
            throw new OrderNotFoundException(orderId);
        }
        return OrderDetailResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderListResponse getMyOrders(Long fanId, Long cursor, int size) {
        List<Order> orders = orderRepository.findByFanId(fanId, cursor, size);
        List<OrderListItemResponse> items = orders.stream()
                .map(OrderListItemResponse::from)
                .toList();
        Long nextCursor = orders.size() == size ? orders.get(orders.size() - 1).getId() : null;
        return new OrderListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public AgencyOrderListResponse getAgencyOrders(Long agencyId, Long artistId, Long cursor, int size) {
        List<AgencyOrderSummary> summaries = orderRepository.findAgencyOrderSummaries(agencyId, artistId, cursor, size);
        List<AgencyOrderListItemResponse> items = summaries.stream()
                .map(AgencyOrderListItemResponse::from)
                .toList();
        Long nextCursor = summaries.size() == size ? summaries.get(summaries.size() - 1).orderId() : null;
        return new AgencyOrderListResponse(items, nextCursor);
    }

    @Transactional
    public void markAsFailed(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.FAILED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        orderRepository.updateStatus(orderId, OrderStatus.FAILED);
    }

    @Transactional
    public void markAsCancelled(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
    }

    @Transactional
    public void markAsPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.RESERVED) {
            return;
        }
        orderRepository.updateStatus(orderId, OrderStatus.PAID);
    }

    @Transactional
    public void markAsCompleted(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.PAID) {
            return;
        }
        orderRepository.updateStatus(orderId, OrderStatus.COMPLETED);
    }

    /** 사용자 취소: RESERVED 상태만 허용. 재고 복구 후 CANCELLED 전이. */
    @Transactional
    public void cancelOrder(Long orderId, Long fanId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (!order.getFanId().equals(fanId)) {
            throw new OrderNotFoundException(orderId);
        }
        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new OrderCancellationNotAllowedException(orderId, order.getStatus());
        }
        for (OrderItem item : order.getItems()) {
            inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), orderId);
        }
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
    }
}
