package com.fandrops.user.infrastructure;

import com.fandrops.user.domain.AuditLog;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaEntity;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogPortAdapterTest {

    @Mock AuditLogJpaRepository auditLogJpaRepository;

    SimpleMeterRegistry meterRegistry;
    AuditLogPortAdapter adapter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        adapter = new AuditLogPortAdapter(auditLogJpaRepository, meterRegistry);
    }

    @Test
    @DisplayName("save 성공 시 fandrops_audit_log_save_total{result=success} 카운터 1 증가")
    void save_success_incrementsSuccessCounter() {
        AuditLog auditLog = sampleAuditLog("ADMIN_BANNER_CREATE");

        adapter.save(auditLog);

        verify(auditLogJpaRepository).save(any(AuditLogJpaEntity.class));
        assertEquals(1.0, successCount("ADMIN_BANNER_CREATE"));
        assertEquals(0.0, failureCount("ADMIN_BANNER_CREATE"));
    }

    @Test
    @DisplayName("save 실패 시 fandrops_audit_log_save_total{result=failure} 카운터 1 증가 + 예외 전파")
    void save_failure_incrementsFailureCounterAndRethrows() {
        AuditLog auditLog = sampleAuditLog("ADMIN_BANNER_DELETE");
        doThrow(new RuntimeException("DB 저장 실패")).when(auditLogJpaRepository).save(any());

        assertThrows(RuntimeException.class, () -> adapter.save(auditLog));

        assertEquals(0.0, successCount("ADMIN_BANNER_DELETE"));
        assertEquals(1.0, failureCount("ADMIN_BANNER_DELETE"));
    }

    @Test
    @DisplayName("서로 다른 action은 각각 독립적인 카운터로 집계된다")
    void save_differentActions_trackedSeparately() {
        adapter.save(sampleAuditLog("ADMIN_BANNER_CREATE"));
        adapter.save(sampleAuditLog("ADMIN_BANNER_CREATE"));
        adapter.save(sampleAuditLog("AGENCY_APPLICATION_APPROVE"));

        assertEquals(2.0, successCount("ADMIN_BANNER_CREATE"));
        assertEquals(1.0, successCount("AGENCY_APPLICATION_APPROVE"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private double successCount(String action) {
        return count(action, "success");
    }

    private double failureCount(String action) {
        return count(action, "failure");
    }

    private double count(String action, String result) {
        Counter counter = meterRegistry.find("fandrops_audit_log_save_total")
                .tag("action", action)
                .tag("result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private AuditLog sampleAuditLog(String action) {
        return AuditLog.builder()
                .occurredAt(Instant.now())
                .actorType("ADMIN")
                .actorId(1L)
                .action(action)
                .resourceType("BANNER")
                .resourceId(1L)
                .traceId("test-trace-id")
                .clientIp("127.0.0.1")
                .build();
    }
}
