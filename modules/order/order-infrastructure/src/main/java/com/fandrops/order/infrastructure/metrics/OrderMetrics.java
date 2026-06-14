package com.fandrops.order.infrastructure.metrics;

import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.infrastructure.persistence.OrderJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 주문 상태별 건수를 Gauge로 노출한다.
 * Grafana P0 알람 "fandrops_orders_status{status="FAILED"}" 메트릭 공급원.
 */
@Component
public class OrderMetrics {

    public OrderMetrics(MeterRegistry registry, OrderJpaRepository orderJpaRepository) {
        for (OrderStatus status : OrderStatus.values()) {
            Gauge.builder("fandrops.orders.status",
                            orderJpaRepository,
                            repo -> repo.countByStatus(status))
                    .tag("status", status.name())
                    .description("주문 상태별 건수")
                    .register(registry);
        }
    }
}
