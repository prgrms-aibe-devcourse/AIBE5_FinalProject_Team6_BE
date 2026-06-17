package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtistMemberJpaRepository extends JpaRepository<ArtistMemberJpaEntity, Long> {
    Optional<ArtistMemberJpaEntity> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
    Optional<ArtistMemberJpaEntity> findByIdAndDeletedAtIsNull(Long id);
}
