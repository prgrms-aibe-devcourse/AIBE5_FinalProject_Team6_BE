package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateOrderCommand;
import com.fandrops.order.application.dto.CreateOrderResult;
import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.exception.OutOfStockException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/** 주문 생성 유스케이스. AccessTicket 검증 → 재고 예약 → RESERVED/CANCELLED 단일 트랜잭션. */
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryReservePort inventoryReservePort;
    private final AccessTicketValidatePort accessTicketValidatePort;
    private final ProductPricePort productPricePort;

    public OrderService(OrderRepository orderRepository, InventoryReservePort inventoryReservePort, AccessTicketValidatePort accessTicketValidatePort, ProductPricePort productPricePort) {
        this.orderRepository = orderRepository;
        this.inventoryReservePort = inventoryReservePort;
        this.accessTicketValidatePort = accessTicketValidatePort;
        this.productPricePort = productPricePort;
    }

    // 재고 부족 예외는 롤백 제외 → CANCELLED 상태가 DB에 커밋되어야 함
    @Transactional(noRollbackFor = {OutOfStockException.class, ReserveConflictException.class})
    public CreateOrderResult createOrder(CreateOrderCommand command) {
        // 1. accessTicket 검증 (실패 시 AccessTicketInvalidException → 403)
        Long primaryProductId = command.getItems().get(0).getProductId();
        accessTicketValidatePort.validate(command.getAccessTicket(), command.getFanId(), primaryProductId);

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

        // 4. 재고 예약 + 상태 전이
        try {
            for (OrderItem item : saved.getItems()) {
                inventoryReservePort.reserve(item.getProductId(), item.getQuantity(), saved.getId());
            }
            orderRepository.updateStatus(saved.getId(), OrderStatus.RESERVED);
            return new CreateOrderResult(saved.getId(), OrderStatus.RESERVED.name(), saved.getOrderPaymentKey());
        } catch (OutOfStockException | ReserveConflictException e) {
            orderRepository.updateStatus(saved.getId(), OrderStatus.CANCELLED);
            throw e;
        }
    }
}
