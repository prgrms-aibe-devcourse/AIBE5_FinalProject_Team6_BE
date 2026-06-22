package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtistMemberJpaRepository extends JpaRepository<ArtistMemberJpaEntity, Long> {
    // 삭제된 멤버 포함 — loginId 중복 체크용
    boolean existsByLoginId(String loginId);
    // 삭제되지 않은 멤버만 — 로그인/단건 조회용
    Optional<ArtistMemberJpaEntity> findByLoginIdAndDeletedAtIsNull(String loginId);
    Optional<ArtistMemberJpaEntity> findByIdAndDeletedAtIsNull(Long id);
    List<ArtistMemberJpaEntity> findByArtistIdAndDeletedAtIsNull(Long artistId);
}
