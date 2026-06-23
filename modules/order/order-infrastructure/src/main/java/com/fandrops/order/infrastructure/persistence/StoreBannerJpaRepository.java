package com.fandrops.order.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreBannerJpaRepository extends JpaRepository<StoreBannerJpaEntity, Long> {

    @Query("""
            SELECT b FROM StoreBannerEntity b
            WHERE b.bannerType = 'STORE'
              AND b.isActive = true
              AND (b.startAt IS NULL OR b.startAt <= :now)
              AND (b.endAt   IS NULL OR b.endAt   >= :now)
            ORDER BY b.exposureOrder ASC
            """)
    List<StoreBannerJpaEntity> findActiveStoreBanners(@Param("now") LocalDateTime now);

    @Query("""
            SELECT b FROM StoreBannerEntity b
            WHERE b.bannerType = 'STORE'
            ORDER BY b.exposureOrder ASC
            """)
    List<StoreBannerJpaEntity> findAllStoreBanners();
}
