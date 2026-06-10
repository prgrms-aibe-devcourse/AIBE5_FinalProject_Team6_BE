package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.BannerRepository;
import com.fandrops.user.domain.Banner;
import com.fandrops.user.domain.BannerType;
import com.fandrops.user.infrastructure.persistence.BannerJpaEntity;
import com.fandrops.user.infrastructure.persistence.BannerJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BannerRepositoryImpl implements BannerRepository {

    private final BannerJpaRepository jpaRepository;

    public BannerRepositoryImpl(BannerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Banner save(Banner banner) {
        return jpaRepository.save(BannerJpaEntity.from(banner)).toDomain();
    }

    @Override
    public Optional<Banner> findById(Long id) {
        return jpaRepository.findById(id).map(BannerJpaEntity::toDomain);
    }

    @Override
    public List<Banner> findActiveMainBanners(LocalDateTime now) {
        return jpaRepository.findActiveByType(BannerType.MAIN, now)
                .stream()
                .map(BannerJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Banner> findAllMainBanners() {
        return jpaRepository.findByBannerTypeOrderByExposureOrderAsc(BannerType.MAIN)
                .stream()
                .map(BannerJpaEntity::toDomain)
                .toList();
    }
}
