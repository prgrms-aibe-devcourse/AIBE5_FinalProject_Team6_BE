package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE occurred_at < :cutoff LIMIT :batchSize",
            nativeQuery = true)
    int deleteOldLogs(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);

    @Query(value = "SELECT COUNT(*) FROM audit_logs WHERE occurred_at < :cutoff",
            nativeQuery = true)
    long countOldLogs(@Param("cutoff") Instant cutoff);
}
