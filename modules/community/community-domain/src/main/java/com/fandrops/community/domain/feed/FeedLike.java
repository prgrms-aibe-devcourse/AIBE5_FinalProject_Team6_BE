package com.fandrops.community.domain.feed;

import com.fandrops.community.domain.feed.exception.FeedDomainException;

import java.time.LocalDateTime;

public class FeedLike {

    private Long id;
    private final Long feedId;
    private final Long fanId;
    private final Long artistMemberId;
    // 조회 최적화: artistMemberId → artistId JOIN 없이 직접 참조 (ERD §5.4)
    private final Long artistId;
    private final LocalDateTime createdAt;

    private FeedLike(Long feedId, Long fanId, Long artistMemberId, Long artistId) {
        validateAuthor(fanId, artistMemberId);
        this.feedId = feedId;
        this.fanId = fanId;
        this.artistMemberId = artistMemberId;
        this.artistId = artistId;
        this.createdAt = LocalDateTime.now();
    }

    private FeedLike(Long id, Long feedId, Long fanId, Long artistMemberId,
                     Long artistId, LocalDateTime createdAt) {
        this.id = id;
        this.feedId = feedId;
        this.fanId = fanId;
        this.artistMemberId = artistMemberId;
        this.artistId = artistId;
        this.createdAt = createdAt;
    }

    public static FeedLike byFan(Long feedId, Long fanId) {
        return new FeedLike(feedId, fanId, null, null);
    }

    public static FeedLike byArtistMember(Long feedId, Long artistMemberId, Long artistId) {
        return new FeedLike(feedId, null, artistMemberId, artistId);
    }

    public static FeedLike reconstruct(Long id, Long feedId, Long fanId, Long artistMemberId,
                                        Long artistId, LocalDateTime createdAt) {
        validateAuthor(fanId, artistMemberId);
        return new FeedLike(id, feedId, fanId, artistMemberId, artistId, createdAt);
    }

    // ERD §5.4: UNIQUE(fan_id, feed_id) · UNIQUE(artist_member_id, feed_id) — fanId XOR artistMemberId
    private static void validateAuthor(Long fanId, Long artistMemberId) {
        boolean hasFan = fanId != null;
        boolean hasArtist = artistMemberId != null;
        if (hasFan == hasArtist) {
            throw new FeedDomainException("좋아요 주체는 fanId 또는 artistMemberId 중 하나만 지정해야 합니다.");
        }
    }

    public Long getId() { return id; }
    public Long getFeedId() { return feedId; }
    public Long getFanId() { return fanId; }
    public Long getArtistMemberId() { return artistMemberId; }
    public Long getArtistId() { return artistId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}