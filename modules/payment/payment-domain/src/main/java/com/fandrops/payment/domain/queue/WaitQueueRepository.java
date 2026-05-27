package com.fandrops.payment.domain.queue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WaitQueueRepository {

    /** 대기열 등록. Terminal 상태가 아닌 entry가 이미 있으면 기존 entry를 반환한다. */
    WaitQueueEntry join(Long fanId, Long productId);

    /** 팬의 현재 대기열 entry 조회. */
    Optional<WaitQueueEntry> findEntry(Long fanId, Long productId);

    /** WAITING 대기열에서 fanId의 현재 순번 (1-based). WAITING 아니거나 없으면 -1. */
    long getPosition(Long fanId, Long productId);

    /** WAITING entry를 대기열에서 제거한다. 이미 PROCESSING/Terminal이면 무시. */
    void exit(Long fanId, Long productId);

    /** WAITING → PROCESSING 원자적 전이. 성공 시 true 반환. */
    boolean transitionToProcessing(Long fanId, Long productId, Instant processingStartAt);

    /** PROCESSING → DONE 또는 EXPIRED 전이. */
    void transitionToTerminal(Long fanId, Long productId, WaitQueueStatus terminal);

    /** joinedAt 오름차순으로 WAITING 상태 상위 limit명의 fanId 반환. */
    List<Long> findTopWaitingFanIds(Long productId, int limit);

    /** processingStartAt이 threshold 이전인 PROCESSING 상태 fanId 반환 (타임아웃 감지). */
    List<Long> findProcessingExpiredFanIds(Long productId, Instant threshold);

    /** 현재 PROCESSING 상태 entry 수. */
    long countProcessing(Long productId);
}