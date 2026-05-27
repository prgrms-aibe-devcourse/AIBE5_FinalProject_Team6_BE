package com.fandrops.payment.domain.queue;

public interface AccessTicketRepository {

    /** fanId × productId 기준 일회용 토큰 발급. 기존 토큰은 덮어쓴다 (W-3: 동시에 1개만). */
    String issue(Long fanId, Long productId);

    /** 토큰 유효성 검증 (만료·불일치 시 false). */
    boolean isValid(String token, Long fanId, Long productId);

    /** 토큰 무효화 (주문 완료·EXPIRED 전이 시 호출). */
    void invalidate(Long fanId, Long productId);
}