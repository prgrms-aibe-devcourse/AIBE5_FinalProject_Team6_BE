package com.fandrops.community.domain.feed;

import com.fandrops.community.domain.feed.exception.FeedDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ArtistFeedTest {

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("피드 생성 시 likeCount·commentCount 0, 필드 정상 설정")
    void create_initialState() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "테스트 내용", clock);

        assertEquals(1L, feed.getArtistId());
        assertEquals(10L, feed.getArtistMemberId());
        assertEquals("테스트 내용", feed.getContent());
        assertEquals(0, feed.getLikeCount());
        assertEquals(0, feed.getCommentCount());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0, 0), feed.getCreatedAt());
        assertNull(feed.getId());
    }

    @Test
    @DisplayName("incrementLikeCount 호출 시 likeCount 1 증가")
    void incrementLikeCount_increases() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        feed.incrementLikeCount();
        assertEquals(1, feed.getLikeCount());
    }

    @Test
    @DisplayName("decrementLikeCount 호출 시 likeCount 1 감소")
    void decrementLikeCount_decreases() {
        ArtistFeed feed = ArtistFeed.reconstruct(1L, 1L, 10L, "내용", 3, 0, LocalDateTime.now(clock));
        feed.decrementLikeCount();
        assertEquals(2, feed.getLikeCount());
    }

    @Test
    @DisplayName("likeCount 0일 때 decrementLikeCount → FeedDomainException")
    void decrementLikeCount_whenZero_throws() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        assertThrows(FeedDomainException.class, feed::decrementLikeCount);
    }

    @Test
    @DisplayName("incrementCommentCount 호출 시 commentCount 1 증가")
    void incrementCommentCount_increases() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        feed.incrementCommentCount();
        assertEquals(1, feed.getCommentCount());
    }

    @Test
    @DisplayName("decrementCommentCount 호출 시 commentCount 1 감소")
    void decrementCommentCount_decreases() {
        ArtistFeed feed = ArtistFeed.reconstruct(1L, 1L, 10L, "내용", 0, 2, LocalDateTime.now(clock));
        feed.decrementCommentCount();
        assertEquals(1, feed.getCommentCount());
    }

    @Test
    @DisplayName("commentCount 0일 때 decrementCommentCount → FeedDomainException")
    void decrementCommentCount_whenZero_throws() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        assertThrows(FeedDomainException.class, feed::decrementCommentCount);
    }

    @Test
    @DisplayName("연속 increment/decrement 후 likeCount 일치")
    void multipleIncrementAndDecrement_likeCountConsistent() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        feed.incrementLikeCount();
        feed.incrementLikeCount();
        feed.incrementLikeCount();
        feed.decrementLikeCount();
        assertEquals(2, feed.getLikeCount());
    }

    @Test
    @DisplayName("연속 increment/decrement 후 commentCount 일치")
    void multipleIncrementAndDecrement_commentCountConsistent() {
        ArtistFeed feed = ArtistFeed.create(1L, 10L, "내용", clock);
        feed.incrementCommentCount();
        feed.incrementCommentCount();
        feed.incrementCommentCount();
        feed.decrementCommentCount();
        assertEquals(2, feed.getCommentCount());
    }
}