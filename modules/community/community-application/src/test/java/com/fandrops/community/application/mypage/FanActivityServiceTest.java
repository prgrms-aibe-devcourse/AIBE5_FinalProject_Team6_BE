package com.fandrops.community.application.mypage;

import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.FeedLike;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FanActivityServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock FeedLikeRepository feedLikeRepository;
    @Mock UserFollowRepository userFollowRepository;

    FanActivityService service;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        service = new FanActivityService(commentRepository, feedLikeRepository, userFollowRepository);
    }

    private Comment comment(Long id, Long feedId, Long artistId, LocalDateTime createdAt) {
        return Comment.reconstruct(id, feedId, artistId, 1L, null, null, "댓글" + id, createdAt);
    }

    private FeedLike like(Long id, Long feedId, Long artistId, LocalDateTime createdAt) {
        return FeedLike.reconstruct(id, feedId, 1L, null, artistId, createdAt);
    }

    private UserFollow follow(Long id, Long fanId, Long artistId, LocalDateTime followedAt) {
        return UserFollow.reconstruct(id, fanId, artistId, followedAt);
    }

    @Nested
    @DisplayName("getActivities")
    class GetActivitiesTest {

        @Test
        @DisplayName("cursor=null 첫 요청 — 댓글·좋아요 createdAt DESC 병합 반환")
        void firstPage_mergesCommentsAndLikes() {
            LocalDateTime t2 = LocalDateTime.of(2026, 6, 1, 12, 0);
            LocalDateTime t1 = LocalDateTime.of(2026, 6, 1, 11, 0);

            when(commentRepository.findByFanId(eq(1L), isNull(), eq(21)))
                    .thenReturn(List.of(comment(2L, 10L, 100L, t2)));
            when(feedLikeRepository.findByFanId(eq(1L), isNull(), eq(21)))
                    .thenReturn(List.of(like(3L, 20L, 200L, t1)));

            ActivityListResult result = service.getActivities(1L, null, 20);

            assertEquals(2, result.items().size());
            assertEquals(ActivityType.COMMENT, result.items().get(0).type());
            assertEquals(ActivityType.FEED_LIKE, result.items().get(1).type());
            assertFalse(result.hasMore());
            assertNull(result.nextCursor());
        }

        @Test
        @DisplayName("size+1 초과 시 hasMore=true, nextCursor 반환")
        void hasMore_whenExceedsSize() {
            LocalDateTime base = LocalDateTime.of(2026, 6, 1, 10, 0);
            List<Comment> comments = new java.util.ArrayList<>();
            for (long i = 21; i >= 1; i--) {
                comments.add(comment(i, 10L, 100L, base.plusMinutes(i)));
            }
            when(commentRepository.findByFanId(eq(1L), isNull(), eq(3)))
                    .thenReturn(comments.subList(0, 3));
            when(feedLikeRepository.findByFanId(eq(1L), isNull(), eq(3)))
                    .thenReturn(List.of());

            ActivityListResult result = service.getActivities(1L, null, 2);

            assertTrue(result.hasMore());
            assertNotNull(result.nextCursor());
            assertEquals(2, result.items().size());
        }

        @Test
        @DisplayName("잘못된 cursor → IllegalArgumentException")
        void invalidCursor_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.getActivities(1L, "not-valid-base64!!!", 20));
        }

        @Test
        @DisplayName("유효한 cursor 디코딩 — commentCursor, likeCursor 전달")
        void validCursor_decodesAndPasses() {
            String encoded = Base64.getEncoder().encodeToString(
                    "{\"c\":5,\"l\":3}".getBytes(StandardCharsets.UTF_8));

            when(commentRepository.findByFanId(eq(1L), eq(5L), eq(21)))
                    .thenReturn(List.of());
            when(feedLikeRepository.findByFanId(eq(1L), eq(3L), eq(21)))
                    .thenReturn(List.of());

            ActivityListResult result = service.getActivities(1L, encoded, 20);

            assertEquals(0, result.items().size());
            assertFalse(result.hasMore());
            verify(commentRepository).findByFanId(1L, 5L, 21);
            verify(feedLikeRepository).findByFanId(1L, 3L, 21);
        }

        @Test
        @DisplayName("댓글만 있을 때 FEED_LIKE cursor는 입력값 유지")
        void onlyComments_likeCursorUnchanged() {
            LocalDateTime t = LocalDateTime.of(2026, 6, 1, 12, 0);
            String inputCursor = Base64.getEncoder().encodeToString(
                    "{\"c\":null,\"l\":10}".getBytes(StandardCharsets.UTF_8));

            when(commentRepository.findByFanId(eq(1L), isNull(), eq(3)))
                    .thenReturn(List.of(comment(5L, 10L, 100L, t), comment(4L, 10L, 100L, t.minusMinutes(1))));
            when(feedLikeRepository.findByFanId(eq(1L), eq(10L), eq(3)))
                    .thenReturn(List.of());

            ActivityListResult result = service.getActivities(1L, inputCursor, 2);

            assertFalse(result.hasMore());
            assertNull(result.nextCursor());
        }
    }

    @Nested
    @DisplayName("getJoinedArtists")
    class GetJoinedArtistsTest {

        @Test
        @DisplayName("cursor=null 첫 요청 — 전체 가입 목록 반환")
        void firstPage_returnsFollows() {
            LocalDateTime t = LocalDateTime.of(2026, 6, 1, 10, 0);
            when(userFollowRepository.findByFanId(eq(1L), isNull(), eq(21)))
                    .thenReturn(List.of(follow(2L, 1L, 100L, t), follow(1L, 1L, 200L, t.minusDays(1))));

            JoinedArtistListResult result = service.getJoinedArtists(1L, null, 20);

            assertEquals(2, result.items().size());
            assertEquals(100L, result.items().get(0).artistId());
            assertFalse(result.hasMore());
            assertNull(result.nextCursor());
        }

        @Test
        @DisplayName("size+1 초과 시 hasMore=true, nextCursor = 마지막 follow id")
        void hasMore_returnsNextCursor() {
            LocalDateTime t = LocalDateTime.of(2026, 6, 1, 10, 0);
            List<UserFollow> follows = List.of(
                    follow(3L, 1L, 100L, t),
                    follow(2L, 1L, 200L, t.minusDays(1)),
                    follow(1L, 1L, 300L, t.minusDays(2)));

            when(userFollowRepository.findByFanId(eq(1L), isNull(), eq(3)))
                    .thenReturn(follows);

            JoinedArtistListResult result = service.getJoinedArtists(1L, null, 2);

            assertTrue(result.hasMore());
            assertEquals("2", result.nextCursor());
            assertEquals(2, result.items().size());
        }

        @Test
        @DisplayName("cursor 전달 시 Long으로 파싱 후 repository에 전달")
        void validCursor_passesCursorId() {
            when(userFollowRepository.findByFanId(eq(1L), eq(5L), eq(21)))
                    .thenReturn(List.of());

            JoinedArtistListResult result = service.getJoinedArtists(1L, "5", 20);

            assertEquals(0, result.items().size());
            verify(userFollowRepository).findByFanId(1L, 5L, 21);
        }

        @Test
        @DisplayName("숫자가 아닌 cursor → IllegalArgumentException")
        void invalidCursor_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.getJoinedArtists(1L, "invalid", 20));
        }
    }
}