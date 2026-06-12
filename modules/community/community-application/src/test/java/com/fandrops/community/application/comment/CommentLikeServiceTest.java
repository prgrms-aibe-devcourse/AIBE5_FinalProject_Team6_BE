package com.fandrops.community.application.comment;

import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.CommentLike;
import com.fandrops.community.domain.feed.repository.CommentLikeRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
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
class CommentLikeServiceTest {

    @Mock CommentLikeRepository commentLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock FanMembershipPort fanMembershipPort;

    CommentLikeService commentLikeService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        commentLikeService = new CommentLikeService(
                commentLikeRepository, commentRepository, fanMembershipPort, clock);
    }

    @Nested
    @DisplayName("likeComment")
    class LikeCommentTest {

        @Test
        @DisplayName("팬 댓글 좋아요 성공")
        void success() {
            Comment comment = Comment.reconstruct(1L, 10L, 20L, 99L, null, null, "댓글", LocalDateTime.now(clock));
            when(commentRepository.findById(eq(1L))).thenReturn(Optional.of(comment));
            when(fanMembershipPort.isFanOf(eq(77L), eq(20L))).thenReturn(true);
            when(commentLikeRepository.existsByCommentIdAndFanId(eq(1L), eq(77L))).thenReturn(false);

            commentLikeService.likeComment(1L, 77L);

            verify(commentLikeRepository).save(any(CommentLike.class));
        }

        @Test
        @DisplayName("댓글 없음 → CommentNotFoundException")
        void comment_notFound_throws() {
            when(commentRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(CommentNotFoundException.class,
                    () -> commentLikeService.likeComment(999L, 77L));
            verifyNoInteractions(commentLikeRepository);
        }

        @Test
        @DisplayName("팬 미가입 → NotFanMemberException")
        void notFanMember_throws() {
            Comment comment = Comment.reconstruct(1L, 10L, 20L, 99L, null, null, "댓글", LocalDateTime.now(clock));
            when(commentRepository.findById(eq(1L))).thenReturn(Optional.of(comment));
            when(fanMembershipPort.isFanOf(eq(77L), eq(20L))).thenReturn(false);

            assertThrows(NotFanMemberException.class,
                    () -> commentLikeService.likeComment(1L, 77L));
            verifyNoInteractions(commentLikeRepository);
        }

        @Test
        @DisplayName("이미 좋아요 → AlreadyLikedException")
        void alreadyLiked_throws() {
            Comment comment = Comment.reconstruct(1L, 10L, 20L, 99L, null, null, "댓글", LocalDateTime.now(clock));
            when(commentRepository.findById(eq(1L))).thenReturn(Optional.of(comment));
            when(fanMembershipPort.isFanOf(eq(77L), eq(20L))).thenReturn(true);
            when(commentLikeRepository.existsByCommentIdAndFanId(eq(1L), eq(77L))).thenReturn(true);

            assertThrows(AlreadyLikedException.class,
                    () -> commentLikeService.likeComment(1L, 77L));
            verify(commentLikeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unlikeComment")
    class UnlikeCommentTest {

        @Test
        @DisplayName("팬 댓글 좋아요 취소 성공")
        void success() {
            CommentLike like = CommentLike.reconstruct(1L, 1L, 77L, LocalDateTime.now(clock));
            when(commentLikeRepository.findByCommentIdAndFanId(eq(1L), eq(77L)))
                    .thenReturn(Optional.of(like));

            commentLikeService.unlikeComment(1L, 77L);

            verify(commentLikeRepository).delete(like);
        }

        @Test
        @DisplayName("좋아요 기록 없음 → LikeNotFoundException")
        void like_notFound_throws() {
            when(commentLikeRepository.findByCommentIdAndFanId(eq(1L), eq(77L)))
                    .thenReturn(Optional.empty());

            assertThrows(LikeNotFoundException.class,
                    () -> commentLikeService.unlikeComment(1L, 77L));
            verify(commentLikeRepository, never()).delete(any());
        }
    }
}