package com.fandrops.order.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageJpaRepository extends JpaRepository<ProductImageJpaEntity, Long> {

    List<ProductImageJpaEntity> findByProductIdOrderBySortOrderAsc(Long productId);

    void deleteByProductId(Long productId);

    @Query("SELECT p FROM ProductImageJpaEntity p WHERE p.productId IN :productIds AND p.primary = true")
    List<ProductImageJpaEntity> findPrimaryByProductIds(@Param("productIds") List<Long> productIds);
}
