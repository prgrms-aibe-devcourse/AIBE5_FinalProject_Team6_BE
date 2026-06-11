package com.fandrops.user.infrastructure;

import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogPurgeJobTest {

    @Mock AuditLogJpaRepository auditLogJpaRepository;

    AuditLogPurgeJob purgeJob;

    @BeforeEach
    void setUp() {
        purgeJob = new AuditLogPurgeJob(auditLogJpaRepository);
    }

    @Test
    @DisplayName("삭제 대상 없으면 deleteOldLogs 호출하지 않음")
    void purge_noTargetRows_skipsDelete() {
        when(auditLogJpaRepository.countOldLogs(any(Instant.class))).thenReturn(0L);

        purgeJob.purge();

        verify(auditLogJpaRepository, never()).deleteOldLogs(any(), anyInt());
    }

    @Test
    @DisplayName("삭제 대상 있으면 deleteChunk를 반복 호출 후 0 반환 시 중단")
    void purge_withTargetRows_deletesInChunksUntilEmpty() {
        when(auditLogJpaRepository.countOldLogs(any(Instant.class))).thenReturn(1200L);
        // 500 → 500 → 200 → 0 순으로 반환 (4번 호출)
        when(auditLogJpaRepository.deleteOldLogs(any(Instant.class), eq(500)))
                .thenReturn(500, 500, 200, 0);

        purgeJob.purge();

        verify(auditLogJpaRepository, times(4)).deleteOldLogs(any(Instant.class), eq(500));
    }

    @Test
    @DisplayName("첫 번째 청크에서 0 반환 시 즉시 중단")
    void purge_firstChunkReturnsZero_stopsImmediately() {
        when(auditLogJpaRepository.countOldLogs(any(Instant.class))).thenReturn(10L);
        when(auditLogJpaRepository.deleteOldLogs(any(Instant.class), eq(500))).thenReturn(0);

        purgeJob.purge();

        verify(auditLogJpaRepository, times(1)).deleteOldLogs(any(), anyInt());
    }
}
