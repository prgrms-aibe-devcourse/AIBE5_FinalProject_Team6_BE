package com.fandrops.order.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    // 상시 상품: dropsStartAt IS NULL, artistId null이면 전체
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.dropsStartAt IS NULL " +
           "AND (:artistId IS NULL OR p.artistId = :artistId) " +
           "AND (:cursor IS NULL OR p.id < :cursor) " +
           "ORDER BY p.id DESC")
    List<ProductJpaEntity> findRegular(@Param("artistId") Long artistId,
                                       @Param("cursor") Long cursor, Pageable pageable);

    // 드롭스 상품: dropsStartAt ≤ now ≤ dropsEndAt, artistId null이면 전체
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.dropsStartAt IS NOT NULL " +
           "AND p.dropsStartAt <= :now AND p.dropsEndAt >= :now " +
           "AND (:artistId IS NULL OR p.artistId = :artistId) " +
           "AND (:cursor IS NULL OR p.id < :cursor) " +
           "ORDER BY p.id DESC")
    List<ProductJpaEntity> findDrops(@Param("now") LocalDateTime now,
                                     @Param("artistId") Long artistId,
                                     @Param("cursor") Long cursor, Pageable pageable);
}
