package com.fandrops.order.infrastructure.persistence;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    // F04-01 상시 상품 목록 — cursor 기반 페이징 (F04-02 드롭스 추가 시 dropsStartAt 필터 추가 예정)
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE (:cursor IS NULL OR p.id < :cursor) " +
           "ORDER BY p.id DESC")
    List<ProductJpaEntity> findRegular(@Param("cursor") Long cursor, Pageable pageable);
}
