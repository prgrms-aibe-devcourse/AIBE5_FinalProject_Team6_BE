package com.fandrops.community.application.feed;

import com.fandrops.community.application.event.NewFeedEvent;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.FeedOwnershipException;
import com.fandrops.community.application.port.FeedCachePort;
import com.fandrops.community.application.port.FeedLikeCachePort;
import com.fandrops.community.application.port.OutboxEvent;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.application.feed.FeedCacheEvictEvent;
import com.fandrops.community.domain.feed.ArtistFeed;
import org.springframework.context.ApplicationEventPublisher;
import com.fandrops.community.domain.feed.FeedImage;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.CommentLikeRepository;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.domain.feed.repository.FeedImageRepository;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class FeedService {

    private final ArtistFeedRepository feedRepository;
    private final FeedImageRepository imageRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final OutboxEventPort outboxEventPort;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;
    private final FeedCachePort feedCachePort;
    private final FeedLikeCachePort feedLikeCachePort;

    public FeedService(ArtistFeedRepository feedRepository,
                       FeedImageRepository imageRepository,
                       FeedLikeRepository feedLikeRepository,
                       CommentRepository commentRepository,
                       CommentLikeRepository commentLikeRepository,
                       OutboxEventPort outboxEventPort,
                       ApplicationEventPublisher applicationEventPublisher,
                       Clock clock,
                       FeedCachePort feedCachePort,
                       FeedLikeCachePort feedLikeCachePort) {
        this.feedRepository = feedRepository;
        this.imageRepository = imageRepository;
        this.feedLikeRepository = feedLikeRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.outboxEventPort = outboxEventPort;
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
        this.feedCachePort = feedCachePort;
        this.feedLikeCachePort = feedLikeCachePort;
    }

    @Transactional
    public FeedResult createFeed(FeedCreateCommand command) {
        ArtistFeed feed = ArtistFeed.create(
                command.artistId(), command.artistMemberId(), command.content(), clock);
        ArtistFeed saved = feedRepository.save(feed);

        List<FeedImage> images = command.imageUrls().stream()
                .map(url -> FeedImage.create(saved.getId(), url, clock))
                .toList();
        List<FeedImage> savedImages = imageRepository.saveAll(images);

        outboxEventPort.publish(new OutboxEvent(
                OutboxEventType.NEW_FEED, saved.getId(),
                Map.of("feedId", saved.getId(),
                       "artistId", saved.getArtistId(),
                       "artistMemberId", saved.getArtistMemberId())
        ));
        applicationEventPublisher.publishEvent(new NewFeedEvent(saved.getId(), saved.getArtistId()));
        // TX commit 후 evict (evict-before-commit 방지)
        applicationEventPublisher.publishEvent(new FeedCacheEvictEvent(saved.getArtistId()));

        return toResult(saved, savedImages, false);
    }

    public FeedListResult getFeeds(Long artistId, String cursor, int size,
                                   Long viewerFanId, Long viewerArtistMemberId) {
        Long cursorId = parseCursor(cursor);
        // SingleFlight + 캐시: 동일 키 동시 miss → DB 쿼리 1회로 수렴
        FeedListResult baseResult = feedCachePort.getOrLoad(artistId, cursorId, size,
                () -> loadFeeds(artistId, cursorId, size));
        return applyIsLiked(baseResult, viewerFanId, viewerArtistMemberId);
    }

    private FeedListResult loadFeeds(Long artistId, Long cursorId, int size) {
        List<ArtistFeed> feeds = feedRepository.findByArtistId(artistId, cursorId, size + 1);
        boolean hasMore = feeds.size() > size;
        List<ArtistFeed> page = hasMore ? feeds.subList(0, size) : feeds;

        List<Long> feedIds = page.stream().map(ArtistFeed::getId).toList();

        List<FeedImage> allImages = feedIds.isEmpty()
                ? List.of()
                : imageRepository.findByFeedIdInOrderByCreatedAt(feedIds);
        Map<Long, List<FeedImage>> imagesByFeedId = allImages.stream()
                .collect(Collectors.groupingBy(FeedImage::getFeedId));

        List<FeedResult> items = page.stream()
                .map(f -> toResult(f, imagesByFeedId.getOrDefault(f.getId(), List.of()), false))
                .toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new FeedListResult(items, nextCursor, hasMore);
    }

    private Long parseCursor(String cursor) {
        if (cursor == null) return null;
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다: " + cursor);
        }
    }

    private FeedListResult applyIsLiked(FeedListResult result, Long viewerFanId, Long viewerArtistMemberId) {
        List<Long> feedIds = result.items().stream().map(FeedResult::id).toList();
        Set<Long> likedFeedIds = resolveLikedFeedIds(feedIds, viewerFanId, viewerArtistMemberId);
        if (likedFeedIds.isEmpty()) {
            return result;
        }
        List<FeedResult> withLiked = result.items().stream()
                .map(f -> likedFeedIds.contains(f.id())
                        ? new FeedResult(f.id(), f.artistId(), f.artistMemberId(), f.content(),
                                f.likeCount(), f.commentCount(), f.imageUrls(), f.createdAt(), true)
                        : f)
                .toList();
        return new FeedListResult(withLiked, result.nextCursor(), result.hasMore());
    }

    private Set<Long> resolveLikedFeedIds(List<Long> feedIds, Long fanId, Long artistMemberId) {
        if (feedIds.isEmpty()) {
            return Set.of();
        }
        if (fanId != null) {
            return feedLikeCachePort.getOrLoad(fanId, feedIds,
                    () -> feedLikeRepository.findLikedFeedIdsByFanId(fanId, feedIds));
        }
        if (artistMemberId != null) {
            return feedLikeRepository.findLikedFeedIdsByArtistMemberId(artistMemberId, feedIds);
        }
        return Set.of();
    }

    public FeedResult getFeed(Long feedId, Long viewerFanId, Long viewerArtistMemberId) {
        ArtistFeed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException("피드를 찾을 수 없습니다."));
        List<FeedImage> images = imageRepository.findByFeedIdInOrderByCreatedAt(List.of(feedId));
        Set<Long> likedFeedIds = resolveLikedFeedIds(List.of(feedId), viewerFanId, viewerArtistMemberId);
        return toResult(feed, images, likedFeedIds.contains(feedId));
    }

    // 단일 TX: comment_like → comment → feed_like → feed_image → feed 순으로 삭제
    @Transactional
    public void deleteFeed(Long feedId, Long requesterArtistMemberId) {
        ArtistFeed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException("피드를 찾을 수 없습니다."));

        if (!feed.getArtistMemberId().equals(requesterArtistMemberId)) {
            throw new FeedOwnershipException("피드 작성자만 삭제할 수 있습니다.");
        }

        commentLikeRepository.deleteByFeedId(feedId);
        commentRepository.deleteByFeedId(feedId);
        feedLikeRepository.deleteByFeedId(feedId);
        imageRepository.deleteByFeedId(feedId);
        feedRepository.delete(feed);
        // TX commit 후 evict (evict-before-commit 방지)
        applicationEventPublisher.publishEvent(new FeedCacheEvictEvent(feed.getArtistId()));
    }

    private FeedResult toResult(ArtistFeed feed, List<FeedImage> images, boolean isLiked) {
        return new FeedResult(
                feed.getId(),
                feed.getArtistId(),
                feed.getArtistMemberId(),
                feed.getContent(),
                feed.getLikeCount(),
                feed.getCommentCount(),
                images.stream().map(FeedImage::getImageUrl).toList(),
                feed.getCreatedAt().atOffset(ZoneOffset.UTC),
                isLiked
        );
    }
}