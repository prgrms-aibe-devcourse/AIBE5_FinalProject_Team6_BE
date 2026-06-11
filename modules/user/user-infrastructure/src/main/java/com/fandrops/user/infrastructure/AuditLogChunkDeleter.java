package com.fandrops.user.infrastructure;

import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class AuditLogChunkDeleter {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogChunkDeleter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    private static final int BATCH_SIZE = 500;

    @Transactional
    public int deleteChunk(Instant cutoff) {
        return auditLogJpaRepository.deleteOldLogs(cutoff, BATCH_SIZE);
    }
}