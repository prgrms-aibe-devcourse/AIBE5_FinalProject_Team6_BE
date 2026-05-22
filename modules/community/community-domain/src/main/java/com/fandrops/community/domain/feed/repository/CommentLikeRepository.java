package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.CommentLike;

import java.util.Optional;

public interface CommentLikeRepository {

    CommentLike save(CommentLike commentLike);

    Optional<CommentLike> findByCommentIdAndFanId(Long commentId, Long fanId);

    boolean existsByCommentIdAndFanId(Long commentId, Long fanId);

    // 댓글 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    void deleteByCommentId(Long commentId);

    void delete(CommentLike commentLike);
}