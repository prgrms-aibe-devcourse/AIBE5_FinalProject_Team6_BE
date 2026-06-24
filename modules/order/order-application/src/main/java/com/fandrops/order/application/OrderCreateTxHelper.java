package com.fandrops.order.application;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.domain.port.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * OrderService.createOrder() TX 분리 Bean.
 *
 * createOrder()는 @Transactional 없이 실행되며, DB 쓰기 작업만 이 Bean의 짧은 TX에 위임한다.
 * 재고 예약 retry 중 outer long TX 커넥션을 점유하지 않아 HikariCP 고갈을 방지한다.
 */
public class OrderCreateTxHelper {

    private final OrderRepository orderRepository;

    public OrderCreateTxHelper(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order savePendingOrder(Order order) {
        return orderRepository.save(order);
    }

    @Transactional
    public void markReserved(Long orderId) {
        orderRepository.updateStatus(orderId, OrderStatus.RESERVED);
    }

    @Transactional
    public void markCancelled(Long orderId) {
        orderRepository.updateStatus(orderId, OrderStatus.CANCELLED);
    }
}
