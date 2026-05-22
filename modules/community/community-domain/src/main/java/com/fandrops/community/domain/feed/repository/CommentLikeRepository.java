package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.CommentLike;

import java.util.Optional;

public interface CommentLikeRepository {

    CommentLike save(CommentLike commentLike);

    Optional<CommentLike> findByCommentIdAndFanId(Long commentId, Long fanId);

    boolean existsByCommentIdAndFanId(Long commentId, Long fanId);

    void delete(CommentLike commentLike);
}