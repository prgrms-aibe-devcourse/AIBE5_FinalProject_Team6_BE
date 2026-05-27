package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArtistFeedJpaRepository extends JpaRepository<ArtistFeedJpaEntity, Long> {

    // 첫 페이지: 최신순 (커서 없음)
    List<ArtistFeedJpaEntity> findByArtistIdOrderByIdDesc(Long artistId, Pageable pageable);

    // 이후 페이지: cursorId 미만의 피드를 최신순 (api-contract.md §2 cursor 방식)
    List<ArtistFeedJpaEntity> findByArtistIdAndIdLessThanOrderByIdDesc(Long artistId, Long id, Pageable pageable);
}