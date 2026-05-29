package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ArtistFeedJpaRepository extends JpaRepository<ArtistFeedJpaEntity, Long> {

    // 첫 페이지: 최신순 (커서 없음)
    List<ArtistFeedJpaEntity> findByArtistIdOrderByIdDesc(Long artistId, Pageable pageable);

    // 이후 페이지: cursorId 미만의 피드를 최신순 (api-contract.md §2 cursor 방식)
    List<ArtistFeedJpaEntity> findByArtistIdAndIdLessThanOrderByIdDesc(Long artistId, Long id, Pageable pageable);

    // lost-update 방지: DB 레벨 atomic increment/decrement
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ArtistFeedJpaEntity f SET f.likeCount = f.likeCount + 1 WHERE f.id = :feedId")
    void incrementLikeCount(@Param("feedId") Long feedId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ArtistFeedJpaEntity f SET f.likeCount = f.likeCount - 1 WHERE f.id = :feedId AND f.likeCount > 0")
    void decrementLikeCount(@Param("feedId") Long feedId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ArtistFeedJpaEntity f SET f.commentCount = f.commentCount + 1 WHERE f.id = :feedId")
    void incrementCommentCount(@Param("feedId") Long feedId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE ArtistFeedJpaEntity f SET f.commentCount = f.commentCount - 1 WHERE f.id = :feedId AND f.commentCount > 0")
    void decrementCommentCount(@Param("feedId") Long feedId);
}