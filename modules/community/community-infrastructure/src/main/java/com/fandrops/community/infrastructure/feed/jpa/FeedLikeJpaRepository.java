package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FeedLikeJpaRepository extends JpaRepository<FeedLikeJpaEntity, Long> {

    Optional<FeedLikeJpaEntity> findByFeedIdAndFanId(Long feedId, Long fanId);

    Optional<FeedLikeJpaEntity> findByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId);

    boolean existsByFeedIdAndFanId(Long feedId, Long fanId);

    boolean existsByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId);

    // /fans/me/activities 팬 좋아요 이력 커서 페이징
    List<FeedLikeJpaEntity> findByFanIdOrderByIdDesc(Long fanId, Pageable pageable);

    List<FeedLikeJpaEntity> findByFanIdAndIdLessThanOrderByIdDesc(Long fanId, Long id, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteByFeedId(Long feedId);
}