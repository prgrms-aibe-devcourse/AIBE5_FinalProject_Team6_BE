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

    // 만료 드롭스: dropsEndAt 지났고 아직 ON_SALE 상태 — 자동 SOLD_OUT 전이 대상. Pageable로 배치 크기 상한 적용
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.dropsStartAt IS NOT NULL " +
           "AND p.dropsEndAt < :now " +
           "AND p.status = 'ON_SALE' " +
           "ORDER BY p.dropsEndAt ASC")
    List<ProductJpaEntity> findExpiredDrops(@Param("now") LocalDateTime now, Pageable pageable);
}
