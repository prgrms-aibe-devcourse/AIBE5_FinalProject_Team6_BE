package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.StoreBanner;
import com.fandrops.order.domain.port.StoreBannerRepository;
import com.fandrops.order.infrastructure.persistence.StoreBannerJpaEntity;
import com.fandrops.order.infrastructure.persistence.StoreBannerJpaRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StoreBannerRepositoryAdapter implements StoreBannerRepository {

    private final StoreBannerJpaRepository jpaRepository;

    @Override
    public StoreBanner save(StoreBanner banner) {
        StoreBannerJpaEntity entity = banner.getId() == null
                ? StoreBannerJpaEntity.from(banner)
                : StoreBannerJpaEntity.fromWithId(banner);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<StoreBanner> findById(Long id) {
        return jpaRepository.findById(id).map(StoreBannerJpaEntity::toDomain);
    }

    @Override
    public List<StoreBanner> findActiveStoreBanners() {
        return jpaRepository.findActiveStoreBanners(LocalDateTime.now(ZoneOffset.UTC)).stream()
                .map(StoreBannerJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
