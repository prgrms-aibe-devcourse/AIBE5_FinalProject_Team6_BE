package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.ArtistFeed;

import java.util.List;
import java.util.Optional;

public interface ArtistFeedRepository {

    ArtistFeed save(ArtistFeed feed);

    Optional<ArtistFeed> findById(Long id);

    // 커서 페이징: cursorId 미만의 피드를 최신순 조회 (api-contract.md cursor 방식)
    List<ArtistFeed> findByArtistId(Long artistId, Long cursorId, int size);

    void delete(ArtistFeed feed);

    void incrementLikeCount(Long feedId);

    void decrementLikeCount(Long feedId);

    void incrementCommentCount(Long feedId);

    void decrementCommentCount(Long feedId);
}