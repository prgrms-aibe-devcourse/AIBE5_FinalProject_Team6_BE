package com.fandrops.order.domain.port;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import java.util.Optional;

/** 주문 저장소 포트. 구현체는 order-infrastructure의 OrderRepositoryAdapter. */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    void updateStatus(Long id, OrderStatus status);
}
