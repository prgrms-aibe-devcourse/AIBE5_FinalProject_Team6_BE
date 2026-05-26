package com.fandrops.community.domain.feed;

import java.time.Clock;
import java.time.LocalDateTime;

public class FeedImage {

    private Long id;
    private final Long feedId;
    private final String imageUrl;
    private final LocalDateTime createdAt;

    private FeedImage(Long feedId, String imageUrl, Clock clock) {
        this.feedId = feedId;
        this.imageUrl = imageUrl;
        this.createdAt = LocalDateTime.now(clock);
    }

    private FeedImage(Long id, Long feedId, String imageUrl, LocalDateTime createdAt) {
        this.id = id;
        this.feedId = feedId;
        this.imageUrl = imageUrl;
        this.createdAt = createdAt;
    }

    public static FeedImage create(Long feedId, String imageUrl, Clock clock) {
        return new FeedImage(feedId, imageUrl, clock);
    }

    public static FeedImage reconstruct(Long id, Long feedId, String imageUrl, LocalDateTime createdAt) {
        return new FeedImage(id, feedId, imageUrl, createdAt);
    }

    public Long getId() { return id; }
    public Long getFeedId() { return feedId; }
    public String getImageUrl() { return imageUrl; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}