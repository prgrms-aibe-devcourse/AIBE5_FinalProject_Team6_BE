package com.fandrops.community.domain.feed;

import java.time.LocalDateTime;

public class CommentLike {

    private Long id;
    private final Long commentId;
    private final Long fanId;
    private final LocalDateTime createdAt;

    private CommentLike(Long commentId, Long fanId) {
        this.commentId = commentId;
        this.fanId = fanId;
        this.createdAt = LocalDateTime.now();
    }

    private CommentLike(Long id, Long commentId, Long fanId, LocalDateTime createdAt) {
        this.id = id;
        this.commentId = commentId;
        this.fanId = fanId;
        this.createdAt = createdAt;
    }

    public static CommentLike create(Long commentId, Long fanId) {
        return new CommentLike(commentId, fanId);
    }

    public static CommentLike reconstruct(Long id, Long commentId, Long fanId, LocalDateTime createdAt) {
        return new CommentLike(id, commentId, fanId, createdAt);
    }

    public Long getId() { return id; }
    public Long getCommentId() { return commentId; }
    public Long getFanId() { return fanId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}