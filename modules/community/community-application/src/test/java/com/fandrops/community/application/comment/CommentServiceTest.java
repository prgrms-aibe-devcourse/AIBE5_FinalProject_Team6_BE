package com.fandrops.community.application.comment;

import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.exception.FeedDomainException;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.domain.feed.ArtistFeed;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock CommentRepository commentRepository;
    @Mock ArtistFeedRepository feedRepository;
    @Mock FanMembershipPort fanMembershipPort;

    CommentService commentService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        commentService = new CommentService(commentRepository, feedRepository, fanMembershipPort, clock);
    }

    @Nested
    @DisplayName("createComment — 팬")
    class FanCommentTest {

        @Test
        @DisplayName("팬 최상위 댓글 작성 성공")
        void fan_topLevel_success() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            Comment saved = Comment.reconstruct(1L, 1L, 10L, 99L, null, null, "댓글", LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(99L), eq(10L))).thenReturn(true);
            when(commentRepository.save(any())).thenReturn(saved);

            CommentResult result = commentService.createComment(
                    new CommentCreateCommand(1L, 10L, 99L, null, null, "댓글"));

            assertEquals(1L, result.id());
            assertEquals(99L, result.fanId());
            assertNull(result.parentId());
            verify(feedRepository).incrementCommentCount(eq(1L));
        }

        @Test
        @DisplayName("팬 대댓글 작성 성공 — parentId 검증 통과")
        void fan_reply_success() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            Comment parent = Comment.reconstruct(2L, 1L, 10L, 77L, null, null, "부모", LocalDateTime.now(clock));
            Comment saved = Comment.reconstruct(3L, 1L, 10L, 99L, null, 2L, "대댓글", LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(99L), eq(10L))).thenReturn(true);
            when(commentRepository.findById(eq(2L))).thenReturn(Optional.of(parent));
            when(commentRepository.save(any())).thenReturn(saved);

            CommentResult result = commentService.createComment(
                    new CommentCreateCommand(1L, 10L, 99L, null, 2L, "대댓글"));

            assertEquals(2L, result.parentId());
            verify(feedRepository).incrementCommentCount(eq(1L));
        }

        @Test
        @DisplayName("피드 없음 → FeedNotFoundException")
        void feed_notFound_throws() {
            when(feedRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(FeedNotFoundException.class,
                    () -> commentService.createComment(
                            new CommentCreateCommand(999L, 10L, 99L, null, null, "댓글")));
            verifyNoInteractions(commentRepository);
        }

        @Test
        @DisplayName("팬 미가입 → NotFanMemberException")
        void fan_notMember_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(99L), eq(10L))).thenReturn(false);

            assertThrows(NotFanMemberException.class,
                    () -> commentService.createComment(
                            new CommentCreateCommand(1L, 10L, 99L, null, null, "댓글")));
            verifyNoInteractions(commentRepository);
        }

        @Test
        @DisplayName("parentId 존재하지 않음 → CommentNotFoundException")
        void parentId_notFound_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(99L), eq(10L))).thenReturn(true);
            when(commentRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(CommentNotFoundException.class,
                    () -> commentService.createComment(
                            new CommentCreateCommand(1L, 10L, 99L, null, 999L, "댓글")));
        }

        @Test
        @DisplayName("parentId feedId 불일치 → IllegalArgumentException")
        void parentId_feedIdMismatch_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            Comment parent = Comment.reconstruct(2L, 99L, 10L, 77L, null, null, "부모", LocalDateTime.now(clock)); // feedId=99
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(fanMembershipPort.isFanOf(eq(99L), eq(10L))).thenReturn(true);
            when(commentRepository.findById(eq(2L))).thenReturn(Optional.of(parent));

            assertThrows(IllegalArgumentException.class,
                    () -> commentService.createComment(
                            new CommentCreateCommand(1L, 10L, 99L, null, 2L, "댓글")));
        }
    }

    @Nested
    @DisplayName("createComment — 아티스트 멤버")
    class ArtistMemberCommentTest {

        @Test
        @DisplayName("아티스트 멤버 답글 작성 성공")
        void artistMember_reply_success() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            Comment parent = Comment.reconstruct(2L, 1L, 10L, 77L, null, null, "팬 댓글", LocalDateTime.now(clock));
            Comment saved = Comment.reconstruct(3L, 1L, 10L, null, 5L, 2L, "아티스트 답글", LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(commentRepository.findById(eq(2L))).thenReturn(Optional.of(parent));
            when(commentRepository.save(any())).thenReturn(saved);

            CommentResult result = commentService.createComment(
                    new CommentCreateCommand(1L, 10L, null, 5L, 2L, "아티스트 답글"));

            assertEquals(5L, result.artistMemberId());
            assertNull(result.fanId());
            verify(feedRepository).incrementCommentCount(eq(1L));
        }

        @Test
        @DisplayName("아티스트 멤버 최상위 댓글 시도 → FeedDomainException (도메인 제약)")
        void artistMember_topLevel_throws() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));

            assertThrows(FeedDomainException.class,
                    () -> commentService.createComment(
                            new CommentCreateCommand(1L, 10L, null, 5L, null, "최상위 댓글")));
            verify(commentRepository, never()).save(any());
        }
    }
}