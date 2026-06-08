package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class ArtistProfileRepositoryImpl implements ArtistProfileRepository {

    private final ArtistProfileJpaRepository jpaRepository;

    public ArtistProfileRepositoryImpl(ArtistProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ArtistProfile save(ArtistProfile profile) {
        return jpaRepository.save(ArtistProfileJpaEntity.from(profile)).toDomain();
    }
}
