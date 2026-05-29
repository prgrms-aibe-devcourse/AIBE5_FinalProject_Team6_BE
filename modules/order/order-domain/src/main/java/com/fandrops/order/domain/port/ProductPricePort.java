package com.fandrops.order.domain.port;

import java.math.BigDecimal;

/** 상품 가격 조회 포트. product 모듈 연동 후 실제 구현체로 교체 예정. */
public interface ProductPricePort {

    /**
     * 주문 시점 상품 가격 조회.
     * MVP 단계에서는 스텁 구현체 사용. product 모듈 연동 후 교체 예정.
     */
    BigDecimal getPrice(Long productId);
}
