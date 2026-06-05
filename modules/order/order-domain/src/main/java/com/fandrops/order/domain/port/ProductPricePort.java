package com.fandrops.order.domain.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 상품 가격 조회 포트. product 모듈 연동 후 실제 구현체로 교체 예정. */
public interface ProductPricePort {

    BigDecimal getPrice(Long productId);

    /** 장바구니 조회 시 N+1 방지용 벌크 가격 조회. product 모듈 연동 후 실제 구현으로 교체. */
    Map<Long, BigDecimal> getPrices(List<Long> productIds);
}
