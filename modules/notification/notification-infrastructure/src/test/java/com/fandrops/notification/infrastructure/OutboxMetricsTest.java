package com.fandrops.notification.infrastructure;

import com.fandrops.notification.domain.OutboxStatus;
import com.fandrops.notification.infrastructure.jpa.NotificationOutboxEventJpaRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    @Test
    void gauge_returns_pending_count() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationOutboxEventJpaRepository repository = mock(NotificationOutboxEventJpaRepository.class);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);

        new OutboxMetrics(registry, repository);

        Gauge gauge = registry.find("fandrops.outbox.pending").gauge();
        assertNotNull(gauge);
        assertEquals(3.0, gauge.value());
    }
}