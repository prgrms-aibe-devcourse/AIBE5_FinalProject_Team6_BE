package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateOrderCommand;
import com.fandrops.order.application.dto.CreateOrderResult;
import com.fandrops.order.application.dto.OrderDetailResponse;
import com.fandrops.order.application.dto.OrderListItemResponse;
import com.fandrops.order.application.dto.OrderListResponse;
import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
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

/** 주문 생성 유스케이스. AccessTicket 검증 → 재고 예약 → RESERVED/CANCELLED 단일 트랜잭션. */
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryReservePort inventoryReservePort;
    private final InventoryRestorePort inventoryRestorePort;
    private final AccessTicketValidatePort accessTicketValidatePort;
    private final ProductPricePort productPricePort;

    public OrderService(OrderRepository orderRepository, InventoryReservePort inventoryReservePort,
                        InventoryRestorePort inventoryRestorePort,
                        AccessTicketValidatePort accessTicketValidatePort, ProductPricePort productPricePort) {
        this.orderRepository = orderRepository;
        this.inventoryReservePort = inventoryReservePort;
        this.inventoryRestorePort = inventoryRestorePort;
        this.accessTicketValidatePort = accessTicketValidatePort;
        this.productPricePort = productPricePort;
    }

    // 재고 부족 예외는 롤백 제외 → CANCELLED 상태가 DB에 커밋되어야 함
    @Transactional(noRollbackFor = {OutOfStockException.class, ReserveConflictException.class})
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

        // 3. 주문 생성 (PENDING) + 저장
        Order order = Order.create(command.getFanId(), items);
        Order saved = orderRepository.save(order);

        // 4. 재고 예약 + 상태 전이 — 부분 성공 시 이미 예약된 아이템 복구 후 CANCELLED
        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : saved.getItems()) {
                inventoryReservePort.reserve(item.getProductId(), item.getQuantity(), saved.getId());
                reserved.add(item);
            }
            orderRepository.updateStatus(saved.getId(), OrderStatus.RESERVED);
            return new CreateOrderResult(saved.getId(), OrderStatus.RESERVED.name(), saved.getOrderPaymentKey());
        } catch (OutOfStockException | ReserveConflictException e) {
            for (OrderItem item : reserved) {
                inventoryRestorePort.restore(item.getProductId(), item.getQuantity(), saved.getId());
            }
            orderRepository.updateStatus(saved.getId(), OrderStatus.CANCELLED);
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
}
