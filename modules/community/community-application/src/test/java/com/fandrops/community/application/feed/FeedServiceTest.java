package com.fandrops.community.application.feed;

import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.FeedOwnershipException;
import com.fandrops.community.domain.feed.ArtistFeed;
import com.fandrops.community.domain.feed.FeedImage;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.CommentLikeRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.domain.feed.repository.FeedImageRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock ArtistFeedRepository feedRepository;
    @Mock FeedImageRepository imageRepository;
    @Mock FeedLikeRepository feedLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock CommentLikeRepository commentLikeRepository;

    FeedService feedService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        feedService = new FeedService(
                feedRepository, imageRepository, feedLikeRepository,
                commentRepository, commentLikeRepository, clock);
    }

    @Nested
    @DisplayName("createFeed")
    class CreateFeedTest {

        @Test
        @DisplayName("피드·이미지 저장 후 FeedResult 반환 (isLiked=false)")
        void savesAndReturnsResult() {
            ArtistFeed saved = ArtistFeed.reconstruct(1L, 10L, 5L, "테스트 내용", 0, 0, LocalDateTime.now(clock));
            FeedImage img = FeedImage.reconstruct(1L, 1L, "https://cdn.example.com/img.jpg", LocalDateTime.now(clock));

            when(feedRepository.save(any())).thenReturn(saved);
            when(imageRepository.saveAll(anyList())).thenReturn(List.of(img));

            FeedResult result = feedService.createFeed(
                    new FeedCreateCommand(10L, 5L, "테스트 내용", List.of("https://cdn.example.com/img.jpg")));

            assertEquals(1L, result.id());
            assertEquals("테스트 내용", result.content());
            assertFalse(result.isLiked());
            assertEquals(1, result.imageUrls().size());
            verify(feedRepository).save(any());
            verify(imageRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("이미지 없는 경우 빈 목록으로 저장")
        void noImages_savesEmptyList() {
            ArtistFeed saved = ArtistFeed.reconstruct(2L, 10L, 5L, "이미지 없음", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.save(any())).thenReturn(saved);
            when(imageRepository.saveAll(anyList())).thenReturn(List.of());

            FeedResult result = feedService.createFeed(
                    new FeedCreateCommand(10L, 5L, "이미지 없음", List.of()));

            assertTrue(result.imageUrls().isEmpty());
            verify(imageRepository).saveAll(argThat(List::isEmpty));
        }
    }

    @Nested
    @DisplayName("getFeeds")
    class GetFeedsTest {

        @Test
        @DisplayName("size+1 조회 후 hasMore=true, nextCursor 설정")
        void hasMore_setsNextCursor() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 0, 0, LocalDateTime.now(clock));
            ArtistFeed f2 = ArtistFeed.reconstruct(2L, 10L, 5L, "피드2", 0, 0, LocalDateTime.now(clock));
            ArtistFeed f3 = ArtistFeed.reconstruct(3L, 10L, 5L, "피드3", 0, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(3))).thenReturn(List.of(f1, f2, f3));
            when(feedLikeRepository.findLikedFeedIdsByFanId(anyLong(), anyList())).thenReturn(Set.of());
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 2, 99L, null);

            assertTrue(result.hasMore());
            assertEquals("2", result.nextCursor());
            assertEquals(2, result.items().size());
        }

        @Test
        @DisplayName("마지막 페이지 hasMore=false, nextCursor=null")
        void lastPage_hasMoreFalse() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 0, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(feedLikeRepository.findLikedFeedIdsByFanId(anyLong(), anyList())).thenReturn(Set.of());
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 20, 99L, null);

            assertFalse(result.hasMore());
            assertNull(result.nextCursor());
        }

        @Test
        @DisplayName("팬 viewer — 좋아요한 피드 isLiked=true")
        void fanViewer_isLikedTrue() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 1, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(feedLikeRepository.findLikedFeedIdsByFanId(eq(99L), anyList())).thenReturn(Set.of(1L));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 20, 99L, null);

            assertTrue(result.items().get(0).isLiked());
            verify(feedLikeRepository).findLikedFeedIdsByFanId(eq(99L), anyList());
            verifyNoMoreInteractions(feedLikeRepository);
        }

        @Test
        @DisplayName("아티스트 멤버 viewer — findLikedFeedIdsByArtistMemberId 호출, isLiked=true")
        void artistMemberViewer_isLikedTrue() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 1, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(feedLikeRepository.findLikedFeedIdsByArtistMemberId(eq(5L), anyList())).thenReturn(Set.of(1L));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 20, null, 5L);

            assertTrue(result.items().get(0).isLiked());
            verify(feedLikeRepository).findLikedFeedIdsByArtistMemberId(eq(5L), anyList());
            verifyNoMoreInteractions(feedLikeRepository);
        }

        @Test
        @DisplayName("비로그인(viewer null) — isLiked=false, FeedLikeRepo 미호출")
        void anonymousViewer_isLikedFalse() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 0, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 20, null, null);

            assertFalse(result.items().get(0).isLiked());
            verifyNoInteractions(feedLikeRepository);
        }

        @Test
        @DisplayName("잘못된 cursor 형식 → IllegalArgumentException (400)")
        void invalidCursor_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> feedService.getFeeds(10L, "abc", 20, null, null));
        }

        @Test
        @DisplayName("숫자 cursor — 파싱 후 Repository에 전달")
        void withCursor_passedToRepository() {
            when(feedRepository.findByArtistId(eq(10L), eq(5L), eq(21))).thenReturn(List.of());

            feedService.getFeeds(10L, "5", 20, null, null);

            verify(feedRepository).findByArtistId(eq(10L), eq(5L), eq(21));
        }

        @Test
        @DisplayName("viewerFanId·viewerArtistMemberId 모두 설정 → fanId 우선, findLikedFeedIdsByFanId만 호출")
        void bothViewerTypes_fanIdTakesPriority() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 0, 0, LocalDateTime.now(clock));

            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(feedLikeRepository.findLikedFeedIdsByFanId(eq(99L), anyList())).thenReturn(Set.of());
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            feedService.getFeeds(10L, null, 20, 99L, 5L);

            verify(feedLikeRepository).findLikedFeedIdsByFanId(eq(99L), anyList());
            verify(feedLikeRepository, never()).findLikedFeedIdsByArtistMemberId(anyLong(), anyList());
        }

        @Test
        @DisplayName("피드 없는 경우 imageRepository 미호출, hasMore=false")
        void noFeeds_imageRepositoryNotCalled() {
            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of());

            FeedListResult result = feedService.getFeeds(10L, null, 20, null, null);

            assertFalse(result.hasMore());
            assertTrue(result.items().isEmpty());
            verifyNoInteractions(imageRepository);
        }
    }

    @Nested
    @DisplayName("deleteFeed")
    class DeleteFeedTest {

        @Test
        @DisplayName("소유자 일치 — cascade 삭제 순서: comment_like → comment → feed_like → image → feed")
        void owner_cascadeDeleteInOrder() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));

            feedService.deleteFeed(1L, 5L);

            var inOrder = inOrder(commentLikeRepository, commentRepository, feedLikeRepository, imageRepository, feedRepository);
            inOrder.verify(commentLikeRepository).deleteByFeedId(eq(1L));
            inOrder.verify(commentRepository).deleteByFeedId(eq(1L));
            inOrder.verify(feedLikeRepository).deleteByFeedId(eq(1L));
            inOrder.verify(imageRepository).deleteByFeedId(eq(1L));
            inOrder.verify(feedRepository).delete(feed);
        }

        @Test
        @DisplayName("존재하지 않는 feedId → FeedNotFoundException, cascade 미실행")
        void notFound_throwsFeedNotFoundException() {
            when(feedRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(FeedNotFoundException.class,
                    () -> feedService.deleteFeed(999L, 5L));
            verifyNoInteractions(commentLikeRepository, commentRepository, feedLikeRepository, imageRepository);
        }

        @Test
        @DisplayName("작성자 불일치 → FeedOwnershipException (403), cascade 및 delete 미실행")
        void notOwner_throwsFeedOwnershipException() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));

            assertThrows(FeedOwnershipException.class,
                    () -> feedService.deleteFeed(1L, 999L));
            verifyNoInteractions(commentLikeRepository, commentRepository, feedLikeRepository, imageRepository);
            verify(feedRepository, never()).delete(any());
        }
    }
}