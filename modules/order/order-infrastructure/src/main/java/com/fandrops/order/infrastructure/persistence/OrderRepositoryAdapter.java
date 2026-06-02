package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderItem;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.OrderRepository;
import java.util.List;
import java.util.Optional;

/** OrderRepository 포트의 JPA 구현체. 엔티티 ↔ 도메인 객체 변환을 담당한다. */
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = new OrderEntity(
                order.getFanId(),
                order.getIdempotencyKey(),
                order.getOrderPaymentKey(),
                order.getStatus(),
                order.getTotalAmount()
        );
        for (OrderItem item : order.getItems()) {
            entity.addItem(new OrderItemEntity(entity, item.getProductId(), item.getQuantity(), item.getUnitPrice()));
        }
        OrderEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public void updateStatus(Long id, OrderStatus status) {
        jpaRepository.updateStatus(id, status);
    }

    private Order toDomain(OrderEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getQuantity(), i.getPrice()))
                .toList();
        return Order.reconstitute(
                entity.getId(),
                entity.getFanId(),
                items,
                entity.getStatus(),
                entity.getTotalAmount(),
                entity.getOrderPaymentKey(),
                entity.getIdempotencyKey()
        );
    }
}
