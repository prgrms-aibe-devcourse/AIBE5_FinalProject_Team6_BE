package com.fandrops.community.domain.feed;

import java.time.Clock;
import java.time.LocalDateTime;

public class ArtistFeed {

    private Long id;
    private final Long artistId;
    private final Long artistMemberId;
    private String content;
    private int likeCount;
    private int commentCount;
    private final LocalDateTime createdAt;

    private ArtistFeed(Long artistId, Long artistMemberId, String content, Clock clock) {
        this.artistId = artistId;
        this.artistMemberId = artistMemberId;
        this.content = content;
        this.likeCount = 0;
        this.commentCount = 0;
        this.createdAt = LocalDateTime.now(clock);
    }

    private ArtistFeed(Long id, Long artistId, Long artistMemberId, String content,
                       int likeCount, int commentCount, LocalDateTime createdAt) {
        this.id = id;
        this.artistId = artistId;
        this.artistMemberId = artistMemberId;
        this.content = content;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.createdAt = createdAt;
    }

    public static ArtistFeed create(Long artistId, Long artistMemberId, String content, Clock clock) {
        return new ArtistFeed(artistId, artistMemberId, content, clock);
    }

    public static ArtistFeed reconstruct(Long id, Long artistId, Long artistMemberId,
                                         String content, int likeCount, int commentCount,
                                         LocalDateTime createdAt) {
        return new ArtistFeed(id, artistId, artistMemberId, content,
                likeCount, commentCount, createdAt);
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount <= 0) {
            throw new IllegalStateException("likeCount가 이미 0입니다.");
        }
        this.likeCount--;
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount <= 0) {
            throw new IllegalStateException("commentCount가 이미 0입니다.");
        }
        this.commentCount--;
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public Long getArtistMemberId() { return artistMemberId; }
    public String getContent() { return content; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}