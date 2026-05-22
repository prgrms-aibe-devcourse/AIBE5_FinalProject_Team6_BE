package com.fandrops.payment.domain.queue;

import java.util.Optional;

public interface WaitQueueRepository {

    /**
     * 대기열 등록. 이미 Terminal 상태(DONE/EXPIRED)가 아닌 entry가 존재하면 기존 entry를 반환한다.
     */
    WaitQueueEntry join(Long fanId, Long productId);

    /**
     * 팬의 현재 대기열 entry 조회.
     */
    Optional<WaitQueueEntry> findEntry(Long fanId, Long productId);

    /**
     * 해당 상품 대기열에서 fanId의 현재 순번 (1-based). 대기열에 없으면 -1.
     */
    long getPosition(Long fanId, Long productId);
}