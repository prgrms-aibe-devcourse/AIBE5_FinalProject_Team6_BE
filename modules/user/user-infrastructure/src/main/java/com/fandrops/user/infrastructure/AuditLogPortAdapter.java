package com.fandrops.user.infrastructure;

import com.fandrops.user.application.port.AuditLogPort;
import com.fandrops.user.domain.AuditLog;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaEntity;
import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogPortAdapter implements AuditLogPort {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogPortAdapter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public void save(AuditLog auditLog) {
        auditLogJpaRepository.save(AuditLogJpaEntity.from(auditLog));
    }
}
