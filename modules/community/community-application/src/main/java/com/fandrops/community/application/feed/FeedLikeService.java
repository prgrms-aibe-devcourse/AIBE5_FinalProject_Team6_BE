package com.fandrops.community.application.feed;

import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.feed.FeedLike;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class FeedLikeService {

    private final ArtistFeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final FanMembershipPort fanMembershipPort;
    private final Clock clock;

    public FeedLikeService(ArtistFeedRepository feedRepository,
                           FeedLikeRepository feedLikeRepository,
                           FanMembershipPort fanMembershipPort,
                           Clock clock) {
        this.feedRepository = feedRepository;
        this.feedLikeRepository = feedLikeRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.clock = clock;
    }

    // fanId XOR artistMemberId — 도메인 FeedLike.validateAuthor에서 재검증
    public void likeFeed(Long feedId, Long fanId, Long artistMemberId, Long artistId) {
        feedRepository.findById(feedId)
                .orElseThrow(() -> new FeedNotFoundException("피드를 찾을 수 없습니다."));

        if (fanId != null) {
            if (!fanMembershipPort.isFanOf(fanId, artistId)) {
                throw new NotFanMemberException("팬 가입 후 좋아요를 누를 수 있습니다.");
            }
            if (feedLikeRepository.existsByFeedIdAndFanId(feedId, fanId)) {
                throw new AlreadyLikedException("이미 좋아요를 눌렀습니다.");
            }
            feedLikeRepository.save(FeedLike.byFan(feedId, fanId, artistId, clock));
        } else {
            if (artistMemberId == null) {
                throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
            }
            if (feedLikeRepository.existsByFeedIdAndArtistMemberId(feedId, artistMemberId)) {
                throw new AlreadyLikedException("이미 좋아요를 눌렀습니다.");
            }
            feedLikeRepository.save(FeedLike.byArtistMember(feedId, artistMemberId, artistId, clock));
        }
        feedRepository.incrementLikeCount(feedId);
    }

    public void unlikeFeed(Long feedId, Long fanId, Long artistMemberId) {
        if (fanId == null && artistMemberId == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
        }
        FeedLike like = fanId != null
                ? feedLikeRepository.findByFeedIdAndFanId(feedId, fanId)
                        .orElseThrow(() -> new LikeNotFoundException("좋아요 기록을 찾을 수 없습니다."))
                : feedLikeRepository.findByFeedIdAndArtistMemberId(feedId, artistMemberId)
                        .orElseThrow(() -> new LikeNotFoundException("좋아요 기록을 찾을 수 없습니다."));
        feedLikeRepository.delete(like);
        feedRepository.decrementLikeCount(feedId);
    }
}
