package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ArtistMemberRepositoryImpl implements ArtistMemberRepository {

    private final ArtistMemberJpaRepository jpaRepository;

    public ArtistMemberRepositoryImpl(ArtistMemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ArtistMember save(ArtistMember member) {
        return jpaRepository.save(ArtistMemberJpaEntity.from(member)).toDomain();
    }

    @Override
    public Optional<ArtistMember> findById(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(ArtistMemberJpaEntity::toDomain);
    }

    @Override
    public Optional<ArtistMember> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId).map(ArtistMemberJpaEntity::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }
}
