package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.port.ProductPricePort;
import com.fandrops.order.infrastructure.persistence.ProductJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductJpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductPriceAdapter implements ProductPricePort {

    private final ProductJpaRepository jpaRepository;

    @Override
    public BigDecimal getPrice(Long productId) {
        return jpaRepository.findById(productId)
                .map(ProductJpaEntity::getPrice)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Override
    public Map<Long, BigDecimal> getPrices(List<Long> productIds) {
        return jpaRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(
                        ProductJpaEntity::getId,
                        ProductJpaEntity::getPrice));
    }
}
