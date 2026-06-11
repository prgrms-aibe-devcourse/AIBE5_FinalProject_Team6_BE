package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.ArtistProfileRepository;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<ArtistProfile> findById(Long artistId) {
        return jpaRepository.findById(artistId).map(ArtistProfileJpaEntity::toDomain);
    }

    @Override
    public List<ArtistProfile> findAllOrderByFanCountDesc(Long cursorId, int size) {
        PageRequest pageable = PageRequest.of(0, size);
        if (cursorId == null) {
            return jpaRepository.findAllByOrderByFanCountDescIdAsc(pageable)
                    .stream().map(ArtistProfileJpaEntity::toDomain).toList();
        }
        return jpaRepository.findAfterCursor(cursorId, pageable)
                .stream().map(ArtistProfileJpaEntity::toDomain).toList();
    }
}
