package com.fandrops.order.application.port;

import com.fandrops.order.application.dto.ProductListResponse;
import java.util.Optional;

public interface ProductCachePort {
    Optional<ProductListResponse> get(String type, Long artistId, Long cursor, int size);
    void put(String type, Long artistId, Long cursor, int size, ProductListResponse result);
    /** 상품 등록·수정·삭제 시 전체 목록 캐시 무효화 */
    void evictAll();
}
