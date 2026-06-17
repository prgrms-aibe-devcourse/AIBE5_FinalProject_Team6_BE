package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.BannerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BannerJpaRepository extends JpaRepository<BannerJpaEntity, Long> {

    @Query("""
            SELECT b FROM BannerJpaEntity b
            WHERE b.bannerType = :type
              AND b.isActive = true
              AND (b.startAt IS NULL OR b.startAt <= :now)
              AND (b.endAt   IS NULL OR b.endAt   >= :now)
            ORDER BY b.exposureOrder ASC
            """)
    List<BannerJpaEntity> findActiveByType(@Param("type") BannerType type,
                                           @Param("now") LocalDateTime now);

    List<BannerJpaEntity> findByBannerTypeOrderByExposureOrderAsc(BannerType bannerType);

    List<BannerJpaEntity> findByBannerTypeAndAgencyIdOrderByExposureOrderAsc(BannerType bannerType, Long agencyId);

    Optional<BannerJpaEntity> findByIdAndAgencyId(Long id, Long agencyId);
}
