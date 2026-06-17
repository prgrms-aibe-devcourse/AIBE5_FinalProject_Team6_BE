package com.fandrops.order.domain.port;

import com.fandrops.order.domain.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(Long id);
    /** artistId null이면 전체 상시 상품 반환 */
    List<Product> findRegularProducts(Long artistId, Long cursor, int size);

    /** dropsStartAt ≤ now ≤ dropsEndAt 조건 드롭스 상품 목록. artistId null이면 전체. */
    List<Product> findDropsProducts(Long artistId, Long cursor, int size);
}
