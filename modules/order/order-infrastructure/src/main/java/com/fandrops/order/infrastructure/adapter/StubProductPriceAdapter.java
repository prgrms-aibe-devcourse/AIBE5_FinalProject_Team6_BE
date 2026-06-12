package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.port.ProductPricePort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** ProductPricePort 스텁 구현체. product 모듈 연동 후 실제 구현으로 교체 예정. */
public class StubProductPriceAdapter implements ProductPricePort {

    // TODO: product 모듈 연동 후 PRODUCT 테이블의 price 컬럼 조회로 교체
    @Override
    public BigDecimal getPrice(Long productId) {
        return BigDecimal.valueOf(10000);
    }

    @Override
    public Map<Long, BigDecimal> getPrices(List<Long> productIds) {
        return productIds.stream()
                .collect(Collectors.toMap(id -> id, id -> BigDecimal.valueOf(10000)));
    }
}
