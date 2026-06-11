package com.fandrops.user.infrastructure;

import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditLogPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeJob.class);
    private static final int RETENTION_DAYS = 365;

    private final AuditLogJpaRepository auditLogJpaRepository;
    private final AuditLogChunkDeleter chunkDeleter;

    public AuditLogPurgeJob(AuditLogJpaRepository auditLogJpaRepository,
                            AuditLogChunkDeleter chunkDeleter) {
        this.auditLogJpaRepository = auditLogJpaRepository;
        this.chunkDeleter = chunkDeleter;
    }

    // 매월 1일 새벽 3시 실행 — data-retention-and-audit-policy.md §5
    @Scheduled(cron = "0 0 3 1 * *")
    public void purge() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);

        long total = auditLogJpaRepository.countOldLogs(cutoff);
        log.info("[AuditLogPurgeJob] 삭제 예정 건수: {}, cutoff={}", total, cutoff);

        if (total == 0) {
            return;
        }

        int totalDeleted = 0;
        int deleted;
        do {
            deleted = chunkDeleter.deleteChunk(cutoff);
            totalDeleted += deleted;
        } while (deleted > 0);

        log.info("[AuditLogPurgeJob] 삭제 완료: {}건", totalDeleted);
    }
}
