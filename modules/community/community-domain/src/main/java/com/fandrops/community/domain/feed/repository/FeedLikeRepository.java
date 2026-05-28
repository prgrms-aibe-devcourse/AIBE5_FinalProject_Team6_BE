package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.FeedLike;

import java.util.List;
import java.util.Optional;

public interface FeedLikeRepository {

    FeedLike save(FeedLike feedLike);

    Optional<FeedLike> findByFeedIdAndFanId(Long feedId, Long fanId);

    Optional<FeedLike> findByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId);

    // 중복 좋아요 체크 (DB UNIQUE 인덱스와 이중 방어 — ERD §5.4)
    boolean existsByFeedIdAndFanId(Long feedId, Long fanId);

    boolean existsByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId);

    // /fans/me/activities — 팬 좋아요 이력 커서 페이징
    List<FeedLike> findByFanId(Long fanId, Long cursorId, int size);

    // 피드 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    void deleteByFeedId(Long feedId);

    void delete(FeedLike feedLike);
}