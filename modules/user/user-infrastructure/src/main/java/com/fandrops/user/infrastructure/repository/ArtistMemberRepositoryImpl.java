package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.ArtistMemberRepository;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaEntity;
import com.fandrops.user.infrastructure.persistence.ArtistMemberJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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
        // 소프트 삭제된 멤버는 로그인 불가
        return jpaRepository.findByLoginIdAndDeletedAtIsNull(loginId).map(ArtistMemberJpaEntity::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return jpaRepository.existsByLoginId(loginId);
    }

    @Override
    public List<ArtistMember> findByArtistId(Long artistId) {
        return jpaRepository.findByArtistIdAndDeletedAtIsNull(artistId).stream()
                .map(ArtistMemberJpaEntity::toDomain)
                .toList();
    }
}
