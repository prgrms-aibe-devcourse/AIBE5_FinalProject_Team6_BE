package com.fandrops.community.application.feed;

import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.FeedOwnershipException;
import com.fandrops.community.application.feed.FeedCacheEvictEvent;
import com.fandrops.community.application.port.FeedCachePort;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import org.springframework.context.ApplicationEventPublisher;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock ArtistFeedRepository feedRepository;
    @Mock FeedImageRepository imageRepository;
    @Mock FeedLikeRepository feedLikeRepository;
    @Mock CommentRepository commentRepository;
    @Mock CommentLikeRepository commentLikeRepository;
    @Mock OutboxEventPort outboxEventPort;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock FeedCachePort feedCachePort;

    FeedService feedService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        feedService = new FeedService(
                feedRepository, imageRepository, feedLikeRepository,
                commentRepository, commentLikeRepository, outboxEventPort,
                applicationEventPublisher, clock, feedCachePort);
        // 기본값: getOrLoad는 loader를 직접 실행 (캐시 miss 시뮬레이션)
        // doAnswer 방식: stub 등록 시 mock 메서드가 호출되지 않아 NPE 방지
        lenient().doAnswer(inv -> inv.<Supplier<FeedListResult>>getArgument(3).get())
                .when(feedCachePort).getOrLoad(anyLong(), any(), anyInt(), any());
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
            verify(outboxEventPort).publish(argThat(e ->
                    OutboxEventType.NEW_FEED == e.type() && e.aggregateId().equals(1L)));
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

    @Nested
    @DisplayName("getFeed — 피드 단건 상세 조회")
    class GetFeedTest {

        @Test
        @DisplayName("피드 조회 성공 — 이미지·isLiked 포함 반환")
        void success_returnsWithImagesAndIsLiked() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 1, 0, LocalDateTime.now(clock));
            FeedImage img = FeedImage.reconstruct(1L, 1L, "https://cdn.example.com/img.jpg", LocalDateTime.now(clock));

            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(List.of(1L))).thenReturn(List.of(img));
            when(feedLikeRepository.findLikedFeedIdsByFanId(eq(99L), anyList())).thenReturn(Set.of(1L));

            FeedResult result = feedService.getFeed(1L, 99L, null);

            assertEquals(1L, result.id());
            assertEquals("내용", result.content());
            assertTrue(result.isLiked());
            assertEquals(1, result.imageUrls().size());
        }

        @Test
        @DisplayName("비로그인 viewer — isLiked=false, FeedLikeRepo 미호출")
        void anonymous_isLikedFalse() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));

            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(List.of(1L))).thenReturn(List.of());

            FeedResult result = feedService.getFeed(1L, null, null);

            assertFalse(result.isLiked());
            verifyNoInteractions(feedLikeRepository);
        }

        @Test
        @DisplayName("아티스트 멤버 viewer — findLikedFeedIdsByArtistMemberId 호출")
        void artistMemberViewer_callsCorrectRepo() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 1, 0, LocalDateTime.now(clock));

            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(List.of(1L))).thenReturn(List.of());
            when(feedLikeRepository.findLikedFeedIdsByArtistMemberId(eq(5L), anyList())).thenReturn(Set.of(1L));

            FeedResult result = feedService.getFeed(1L, null, 5L);

            assertTrue(result.isLiked());
            verify(feedLikeRepository).findLikedFeedIdsByArtistMemberId(eq(5L), anyList());
            verify(feedLikeRepository, never()).findLikedFeedIdsByFanId(anyLong(), anyList());
        }

        @Test
        @DisplayName("존재하지 않는 feedId → FeedNotFoundException")
        void notFound_throws() {
            when(feedRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(FeedNotFoundException.class,
                    () -> feedService.getFeed(999L, null, null));
            verifyNoInteractions(imageRepository, feedLikeRepository);
        }
    }

    @Nested
    @DisplayName("getFeeds - 캐시 (SingleFlight)")
    class GetFeedsCacheTest {

        @Test
        @DisplayName("캐시 hit → getOrLoad가 loader 미실행, feedRepository·imageRepository 미호출, isLiked 적용")
        void cacheHit_doesNotCallRepositoryAndAppliesIsLiked() {
            FeedResult cachedItem = new FeedResult(1L, 10L, 5L, "캐시 피드", 0, 0,
                    List.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"), false);
            FeedListResult cachedResult = new FeedListResult(List.of(cachedItem), null, false);
            doReturn(cachedResult).when(feedCachePort).getOrLoad(eq(10L), isNull(), eq(20), any());
            when(feedLikeRepository.findLikedFeedIdsByFanId(eq(99L), anyList())).thenReturn(Set.of(1L));

            FeedListResult result = feedService.getFeeds(10L, null, 20, 99L, null);

            verifyNoInteractions(feedRepository, imageRepository);
            assertTrue(result.items().get(0).isLiked());
        }

        @Test
        @DisplayName("캐시 hit, 비로그인 → isLiked=false, feedLikeRepo 미호출")
        void cacheHit_anonymous_isLikedFalseNoLikeQuery() {
            FeedResult cachedItem = new FeedResult(1L, 10L, 5L, "캐시 피드", 0, 0,
                    List.of(), OffsetDateTime.parse("2026-06-01T00:00:00Z"), false);
            FeedListResult cachedResult = new FeedListResult(List.of(cachedItem), null, false);
            doReturn(cachedResult).when(feedCachePort).getOrLoad(eq(10L), isNull(), eq(20), any());

            FeedListResult result = feedService.getFeeds(10L, null, 20, null, null);

            verifyNoInteractions(feedRepository, imageRepository, feedLikeRepository);
            assertFalse(result.items().get(0).isLiked());
        }

        @Test
        @DisplayName("캐시 miss → getOrLoad가 loader 실행, feedRepository 호출")
        void cacheMiss_getOrLoadExecutesLoader() {
            ArtistFeed f1 = ArtistFeed.reconstruct(1L, 10L, 5L, "피드1", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findByArtistId(eq(10L), isNull(), eq(21))).thenReturn(List.of(f1));
            when(imageRepository.findByFeedIdInOrderByCreatedAt(anyList())).thenReturn(List.of());

            feedService.getFeeds(10L, null, 20, null, null);

            verify(feedCachePort).getOrLoad(eq(10L), isNull(), eq(20), any());
            verify(feedRepository).findByArtistId(eq(10L), isNull(), eq(21));
        }
    }

    @Nested
    @DisplayName("createFeed - 캐시 evict 이벤트")
    class CreateFeedCacheEvictTest {

        @Test
        @DisplayName("createFeed 성공 → FeedCacheEvictEvent 발행 (TX commit 후 evict)")
        void createFeed_publishesCacheEvictEvent() {
            ArtistFeed saved = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.save(any())).thenReturn(saved);
            when(imageRepository.saveAll(anyList())).thenReturn(List.of());

            feedService.createFeed(new FeedCreateCommand(10L, 5L, "내용", List.of()));

            verify(applicationEventPublisher).publishEvent(argThat((Object e) ->
                    e instanceof FeedCacheEvictEvent evt && evt.artistId().equals(10L)));
        }
    }

    @Nested
    @DisplayName("deleteFeed - 캐시 evict 이벤트")
    class DeleteFeedCacheEvictTest {

        @Test
        @DisplayName("deleteFeed 성공 → FeedCacheEvictEvent 발행 (TX commit 후 evict)")
        void deleteFeed_publishesCacheEvictEvent() {
            ArtistFeed feed = ArtistFeed.reconstruct(1L, 10L, 5L, "내용", 0, 0, LocalDateTime.now(clock));
            when(feedRepository.findById(eq(1L))).thenReturn(Optional.of(feed));

            feedService.deleteFeed(1L, 5L);

            verify(applicationEventPublisher).publishEvent(argThat((Object e) ->
                    e instanceof FeedCacheEvictEvent evt && evt.artistId().equals(10L)));
        }
    }
}