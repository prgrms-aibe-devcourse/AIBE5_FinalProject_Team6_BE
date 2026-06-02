package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.CommentLike;

import java.util.Optional;

public interface CommentLikeRepository {

    CommentLike save(CommentLike commentLike);

    Optional<CommentLike> findByCommentIdAndFanId(Long commentId, Long fanId);

    boolean existsByCommentIdAndFanId(Long commentId, Long fanId);

    // 댓글 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    void deleteByCommentId(Long commentId);

    // 피드 삭제 시 해당 피드 전체 댓글의 좋아요 일괄 삭제
    void deleteByFeedId(Long feedId);

    void delete(CommentLike commentLike);
}