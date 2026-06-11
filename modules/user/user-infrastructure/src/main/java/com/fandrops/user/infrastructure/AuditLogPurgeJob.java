package com.fandrops.user.infrastructure;

import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditLogPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(AuditLogPurgeJob.class);
    private static final int BATCH_SIZE = 500;
    private static final int RETENTION_DAYS = 365;

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogPurgeJob(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
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
            deleted = deleteChunk(cutoff);
            totalDeleted += deleted;
        } while (deleted > 0);

        log.info("[AuditLogPurgeJob] 삭제 완료: {}건", totalDeleted);
    }

    @Transactional
    public int deleteChunk(Instant cutoff) {
        return auditLogJpaRepository.deleteOldLogs(cutoff, BATCH_SIZE);
    }
}
