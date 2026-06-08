package com.fandrops.payment.domain.payment;

import java.util.Optional;

/** orders 테이블에서 fanId를 조회하는 아웃바운드 포트. payment-infrastructure가 native query로 구현. */
public interface OrderFanQueryPort {

    Optional<Long> findFanIdByOrderId(Long orderId);
}