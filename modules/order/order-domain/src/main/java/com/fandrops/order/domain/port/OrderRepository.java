package com.fandrops.order.domain.port;

import com.fandrops.order.domain.Order;
import com.fandrops.order.domain.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 주문 저장소 포트. 구현체는 order-infrastructure의 OrderRepositoryAdapter. */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    void updateStatus(Long id, OrderStatus status);

    /** 특정 상태이면서 updatedAt이 cutoff보다 오래된 주문 목록을 반환한다. 복구 스케줄러용. */
    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    /** fanId 기준 최신순 cursor-based 페이지네이션. cursor 미제공 시 첫 페이지. */
    List<Order> findByFanId(Long fanId, Long cursor, int size);
}
