package com.fandrops.user.infrastructure;

import com.fandrops.user.infrastructure.persistence.AuditLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogChunkDeleterTest {

    @Mock AuditLogJpaRepository auditLogJpaRepository;

    AuditLogChunkDeleter chunkDeleter;

    @BeforeEach
    void setUp() {
        chunkDeleter = new AuditLogChunkDeleter(auditLogJpaRepository);
    }

    @Test
    @DisplayName("deleteChunk — 500개 단위로 잘라 deleteOldLogs에 위임하고 삭제 건수를 반환한다")
    void deleteChunk_delegatesToRepository_withBatchSize500() {
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        when(auditLogJpaRepository.deleteOldLogs(cutoff, 500)).thenReturn(300);

        int result = chunkDeleter.deleteChunk(cutoff);

        assertEquals(300, result);
        verify(auditLogJpaRepository).deleteOldLogs(eq(cutoff), eq(500));
    }

    @Test
    @DisplayName("deleteChunk — 삭제 대상 없으면 0 반환")
    void deleteChunk_returnsZeroWhenNothingDeleted() {
        when(auditLogJpaRepository.deleteOldLogs(any(Instant.class), anyInt())).thenReturn(0);

        int result = chunkDeleter.deleteChunk(Instant.now());

        assertEquals(0, result);
    }

    @Test
    @DisplayName("deleteChunk — BATCH_SIZE 정확히 500 건 삭제 시 500 반환")
    void deleteChunk_returnsFullBatchSize_whenExactlyFiveHundredDeleted() {
        when(auditLogJpaRepository.deleteOldLogs(any(Instant.class), anyInt())).thenReturn(500);

        int result = chunkDeleter.deleteChunk(Instant.now());

        assertEquals(500, result);
    }
}
