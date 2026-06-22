package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.ProductImage;
import com.fandrops.order.domain.port.ProductImageRepository;
import com.fandrops.order.infrastructure.persistence.ProductImageJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductImageJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ProductImageRepositoryAdapter implements ProductImageRepository {

    private final ProductImageJpaRepository jpaRepository;

    @Override
    public void saveAll(List<ProductImage> images) {
        jpaRepository.saveAll(images.stream().map(ProductImageJpaEntity::from).toList());
    }

    @Override
    public void deleteByProductId(Long productId) {
        jpaRepository.deleteByProductId(productId);
    }

    @Override
    public List<ProductImage> findByProductId(Long productId) {
        return jpaRepository.findByProductIdOrderBySortOrderAsc(productId).stream()
                .map(ProductImageJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Map<Long, String> findThumbnailsByProductIds(List<Long> productIds) {
        return jpaRepository.findPrimaryByProductIds(productIds).stream()
                .collect(Collectors.toMap(ProductImageJpaEntity::getProductId, ProductImageJpaEntity::getImageUrl));
    }
}
