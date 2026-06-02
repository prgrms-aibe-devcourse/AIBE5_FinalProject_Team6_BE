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
    private final Clock clock;

    public FeedService(ArtistFeedRepository feedRepository,
                       FeedImageRepository imageRepository,
                       FeedLikeRepository feedLikeRepository,
                       CommentRepository commentRepository,
                       CommentLikeRepository commentLikeRepository,
                       Clock clock) {
        this.feedRepository = feedRepository;
        this.imageRepository = imageRepository;
        this.feedLikeRepository = feedLikeRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.clock = clock;
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

        return toResult(saved, savedImages, false);
    }

    public FeedListResult getFeeds(Long artistId, String cursor, int size,
                                   Long viewerFanId, Long viewerArtistMemberId) {
        Long cursorId = null;
        if (cursor != null) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cursor 형식이 올바르지 않습니다: " + cursor);
            }
        }
        List<ArtistFeed> feeds = feedRepository.findByArtistId(artistId, cursorId, size + 1);
        boolean hasMore = feeds.size() > size;
        List<ArtistFeed> page = hasMore ? feeds.subList(0, size) : feeds;

        List<Long> feedIds = page.stream().map(ArtistFeed::getId).toList();
        Set<Long> likedFeedIds = resolveLikedFeedIds(feedIds, viewerFanId, viewerArtistMemberId);

        List<FeedImage> allImages = feedIds.isEmpty()
                ? List.of()
                : imageRepository.findByFeedIdInOrderByCreatedAt(feedIds);
        Map<Long, List<FeedImage>> imagesByFeedId = allImages.stream()
                .collect(Collectors.groupingBy(FeedImage::getFeedId));

        List<FeedResult> items = page.stream()
                .map(f -> toResult(f, imagesByFeedId.getOrDefault(f.getId(), List.of()),
                        likedFeedIds.contains(f.getId())))
                .toList();
        String nextCursor = hasMore ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return new FeedListResult(items, nextCursor, hasMore);
    }

    private Set<Long> resolveLikedFeedIds(List<Long> feedIds, Long fanId, Long artistMemberId) {
        if (feedIds.isEmpty()) {
            return Set.of();
        }
        if (fanId != null) {
            return feedLikeRepository.findLikedFeedIdsByFanId(fanId, feedIds);
        }
        if (artistMemberId != null) {
            return feedLikeRepository.findLikedFeedIdsByArtistMemberId(artistMemberId, feedIds);
        }
        return Set.of();
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