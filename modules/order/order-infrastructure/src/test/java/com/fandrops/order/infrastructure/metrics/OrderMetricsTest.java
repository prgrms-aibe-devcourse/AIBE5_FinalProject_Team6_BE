package com.fandrops.order.infrastructure.metrics;

import com.fandrops.order.domain.OrderStatus;
import com.fandrops.order.infrastructure.persistence.OrderJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderMetrics 단위 테스트")
class OrderMetricsTest {

    @Mock
    private OrderJpaRepository orderJpaRepository;

    @Test
    @DisplayName("모든 OrderStatus에 대해 Gauge가 등록된다")
    void allStatusGaugesRegistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new OrderMetrics(registry, orderJpaRepository);

        for (OrderStatus status : OrderStatus.values()) {
            Gauge gauge = registry.find("fandrops.orders.status")
                    .tag("status", status.name())
                    .gauge();
            assertNotNull(gauge, status.name() + " Gauge가 등록되지 않았습니다");
        }
    }

    @Test
    @DisplayName("FAILED Gauge — countByStatus 위임값 반환")
    void failedGauge_returnsCountFromRepository() {
        MeterRegistry registry = new SimpleMeterRegistry();
        given(orderJpaRepository.countByStatus(OrderStatus.FAILED)).willReturn(3L);
        new OrderMetrics(registry, orderJpaRepository);

        double value = registry.find("fandrops.orders.status")
                .tag("status", "FAILED")
                .gauge()
                .value();

        assertEquals(3.0, value);
    }

    @Test
    @DisplayName("주문 없으면 Gauge 값 0 반환")
    void gauge_noOrders_returnsZero() {
        MeterRegistry registry = new SimpleMeterRegistry();
        given(orderJpaRepository.countByStatus(OrderStatus.RESERVED)).willReturn(0L);
        new OrderMetrics(registry, orderJpaRepository);

        double value = registry.find("fandrops.orders.status")
                .tag("status", "RESERVED")
                .gauge()
                .value();

        assertEquals(0.0, value);
    }
}
