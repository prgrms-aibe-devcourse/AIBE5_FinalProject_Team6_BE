package com.fandrops.user.infrastructure;

import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.AuditLog;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaEntity;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogPortAdapter implements AuditLogPort {

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final MeterRegistry meterRegistry;

    public AuditLogPortAdapter(AuditLogJpaRepository auditLogJpaRepository,
                               MeterRegistry meterRegistry) {
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void save(AuditLog auditLog) {
        try {
            auditLogJpaRepository.save(AuditLogJpaEntity.from(auditLog));
            meterRegistry.counter("fandrops_audit_log_save_total",
                    "action", auditLog.getAction(),
                    "result", "success").increment();
        } catch (Exception e) {
            meterRegistry.counter("fandrops_audit_log_save_total",
                    "action", auditLog.getAction(),
                    "result", "failure").increment();
            throw e;
        }
    }
}
