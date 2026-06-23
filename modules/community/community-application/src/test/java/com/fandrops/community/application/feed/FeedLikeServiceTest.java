package com.fandrops.community.application.feed;

import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.application.port.FeedLikeCachePort;
import com.fandrops.community.domain.feed.ArtistFeed;
import com.fandrops.community.domain.feed.FeedLike;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedLikeServiceTest {

    @Mock ArtistFeedRepository feedRepository;
    @Mock FeedLikeRepository feedLikeRepository;
    @Mock FanMembershipPort fanMembershipPort;
    @Mock FeedLikeCachePort feedLikeCachePort;

    FeedLikeService feedLikeService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        feedLikeService = new FeedLikeService(feedRepository, feedLikeRepository, fanMembershipPort, feedLikeCachePort, clock);
    }

    @Nested
    @DisplayName("likeFeed")
    class LikeFeedTest {

        @Test
        @DisplayName("팬 피드 좋아요 성공 — likeCount 증가, feedLikeCache evict 호출")
        void fan_success() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(true);
            when(feedLikeRepository.existsByFeedIdAndFanId(eq(1L), eq(77L))).thenReturn(false);

            feedLikeService.likeFeed(1L, 77L, null, 10L);

            verify(feedLikeRepository).save(any(FeedLike.class));
            verify(feedRepository).incrementLikeCount(eq(1L));
            verify(feedLikeCachePort).evictByFanId(eq(77L));
        }

        @Test
        @DisplayName("아티스트 멤버 피드 좋아요 성공 — likeCount 증가, feedLikeCache evict 미호출")
        void artistMember_success() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndArtistMemberId(eq(1L), eq(5L))).thenReturn(false);

            feedLikeService.likeFeed(1L, null, 5L, 10L);

            verify(feedLikeRepository).save(any(FeedLike.class));
            verify(feedRepository).incrementLikeCount(eq(1L));
            verifyNoInteractions(fanMembershipPort, feedLikeCachePort);
        }

        @Test
        @DisplayName("피드 없음 → FeedNotFoundException")
        void feed_notFound_throws() {
            when(feedRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(FeedNotFoundException.class,
                    () -> feedLikeService.likeFeed(999L, 77L, null, 10L));
            verifyNoInteractions(feedLikeRepository);
        }

        @Test
        @DisplayName("팬 미가입 → NotFanMemberException")
        void fan_notMember_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);

            assertThrows(NotFanMemberException.class,
                    () -> feedLikeService.likeFeed(1L, 77L, null, 10L));
            verify(feedLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("팬 중복 좋아요 → AlreadyLikedException")
        void fan_alreadyLiked_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(true);
            when(feedLikeRepository.existsByFeedIdAndFanId(eq(1L), eq(77L))).thenReturn(true);

            assertThrows(AlreadyLikedException.class,
                    () -> feedLikeService.likeFeed(1L, 77L, null, 10L));
            verify(feedLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("아티스트 멤버 중복 좋아요 → AlreadyLikedException")
        void artistMember_alreadyLiked_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(feedLikeRepository.existsByFeedIdAndArtistMemberId(eq(1L), eq(5L))).thenReturn(true);

            assertThrows(AlreadyLikedException.class,
                    () -> feedLikeService.likeFeed(1L, null, 5L, 10L));
            verify(feedLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("fanId·artistMemberId 모두 null → IllegalArgumentException")
        void bothNull_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));

            assertThrows(IllegalArgumentException.class,
                    () -> feedLikeService.likeFeed(1L, null, null, 10L));
        }
    }

    @Nested
    @DisplayName("unlikeFeed")
    class UnlikeFeedTest {

        @Test
        @DisplayName("팬 좋아요 취소 성공 — likeCount 감소, feedLikeCache evict 호출")
        void fan_success() {
            FeedLike like = FeedLike.reconstruct(1L, 1L, 77L, null, null, LocalDateTime.now(clock));
            when(feedLikeRepository.findByFeedIdAndFanId(eq(1L), eq(77L))).thenReturn(Optional.of(like));

            feedLikeService.unlikeFeed(1L, 77L, null);

            verify(feedLikeRepository).delete(like);
            verify(feedRepository).decrementLikeCount(eq(1L));
            verify(feedLikeCachePort).evictByFanId(eq(77L));
        }

        @Test
        @DisplayName("아티스트 멤버 좋아요 취소 성공 — likeCount 감소, feedLikeCache evict 미호출")
        void artistMember_success() {
            FeedLike like = FeedLike.reconstruct(1L, 1L, null, 5L, 10L, LocalDateTime.now(clock));
            when(feedLikeRepository.findByFeedIdAndArtistMemberId(eq(1L), eq(5L))).thenReturn(Optional.of(like));

            feedLikeService.unlikeFeed(1L, null, 5L);

            verify(feedLikeRepository).delete(like);
            verify(feedRepository).decrementLikeCount(eq(1L));
            verifyNoInteractions(feedLikeCachePort);
        }

        @Test
        @DisplayName("팬 좋아요 기록 없음 → LikeNotFoundException")
        void fan_likeNotFound_throws() {
            when(feedLikeRepository.findByFeedIdAndFanId(eq(1L), eq(77L))).thenReturn(Optional.empty());

            assertThrows(LikeNotFoundException.class,
                    () -> feedLikeService.unlikeFeed(1L, 77L, null));
            verify(feedLikeRepository, never()).delete(any());
            verify(feedRepository, never()).decrementLikeCount(any());
        }

        @Test
        @DisplayName("아티스트 멤버 좋아요 기록 없음 → LikeNotFoundException")
        void artistMember_likeNotFound_throws() {
            when(feedLikeRepository.findByFeedIdAndArtistMemberId(eq(1L), eq(5L))).thenReturn(Optional.empty());

            assertThrows(LikeNotFoundException.class,
                    () -> feedLikeService.unlikeFeed(1L, null, 5L));
            verify(feedLikeRepository, never()).delete(any());
        }
    }
}