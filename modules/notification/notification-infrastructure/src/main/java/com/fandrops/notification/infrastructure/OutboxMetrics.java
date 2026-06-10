package com.fandrops.notification.infrastructure;

import com.fandrops.notification.domain.OutboxStatus;
import com.fandrops.notification.infrastructure.jpa.NotificationOutboxEventJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, NotificationOutboxEventJpaRepository repository) {
        Gauge.builder("fandrops.outbox.pending", repository,
                        repo -> repo.countByStatus(OutboxStatus.PENDING))
                .description("처리 대기 중인 Outbox 이벤트 수")
                .register(registry);
    }
}
