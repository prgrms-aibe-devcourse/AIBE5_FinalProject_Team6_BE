package com.fandrops.order.domain.port;

import com.fandrops.order.domain.ProductImage;
import java.util.List;
import java.util.Map;

public interface ProductImageRepository {
    void saveAll(List<ProductImage> images);
    void deleteByProductId(Long productId);
    List<ProductImage> findByProductId(Long productId);
    /** productId 목록의 대표 이미지(isPrimary=true)를 productId → imageUrl 맵으로 반환 */
    Map<Long, String> findThumbnailsByProductIds(List<Long> productIds);
}
