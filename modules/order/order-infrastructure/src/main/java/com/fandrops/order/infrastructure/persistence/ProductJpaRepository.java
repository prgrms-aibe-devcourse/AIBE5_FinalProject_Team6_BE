package com.fandrops.order.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    // 상시 상품: dropsStartAt IS NULL
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.dropsStartAt IS NULL " +
           "AND (:cursor IS NULL OR p.id < :cursor) " +
           "ORDER BY p.id DESC")
    List<ProductJpaEntity> findRegular(@Param("cursor") Long cursor, Pageable pageable);

    // 드롭스 상품: dropsStartAt ≤ now ≤ dropsEndAt
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.dropsStartAt IS NOT NULL " +
           "AND p.dropsStartAt <= :now AND p.dropsEndAt >= :now " +
           "AND (:cursor IS NULL OR p.id < :cursor) " +
           "ORDER BY p.id DESC")
    List<ProductJpaEntity> findDrops(@Param("now") LocalDateTime now,
                                     @Param("cursor") Long cursor, Pageable pageable);
}
