package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.domain.feed.FeedLike;
import com.fandrops.community.domain.feed.repository.FeedLikeRepository;
import com.fandrops.community.infrastructure.feed.jpa.FeedLikeJpaEntity;
import com.fandrops.community.infrastructure.feed.jpa.FeedLikeJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class FeedLikeRepositoryAdapter implements FeedLikeRepository {

    private final FeedLikeJpaRepository jpaRepository;

    public FeedLikeRepositoryAdapter(FeedLikeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public FeedLike save(FeedLike feedLike) {
        return toDomain(jpaRepository.save(toJpa(feedLike)));
    }

    @Override
    public Optional<FeedLike> findByFeedIdAndFanId(Long feedId, Long fanId) {
        return jpaRepository.findByFeedIdAndFanId(feedId, fanId).map(this::toDomain);
    }

    @Override
    public Optional<FeedLike> findByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId) {
        return jpaRepository.findByFeedIdAndArtistMemberId(feedId, artistMemberId).map(this::toDomain);
    }

    @Override
    public boolean existsByFeedIdAndFanId(Long feedId, Long fanId) {
        return jpaRepository.existsByFeedIdAndFanId(feedId, fanId);
    }

    @Override
    public boolean existsByFeedIdAndArtistMemberId(Long feedId, Long artistMemberId) {
        return jpaRepository.existsByFeedIdAndArtistMemberId(feedId, artistMemberId);
    }

    @Override
    public Set<Long> findLikedFeedIdsByFanId(Long fanId, List<Long> feedIds) {
        return jpaRepository.findByFanIdAndFeedIdIn(fanId, feedIds).stream()
                .map(FeedLikeJpaEntity::getFeedId)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Long> findLikedFeedIdsByArtistMemberId(Long artistMemberId, List<Long> feedIds) {
        return jpaRepository.findByArtistMemberIdAndFeedIdIn(artistMemberId, feedIds).stream()
                .map(FeedLikeJpaEntity::getFeedId)
                .collect(Collectors.toSet());
    }

    @Override
    public List<FeedLike> findByFanId(Long fanId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<FeedLikeJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByFanIdOrderByIdDesc(fanId, page)
                : jpaRepository.findByFanIdAndIdLessThanOrderByIdDesc(fanId, cursorId, page);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByFeedId(Long feedId) {
        jpaRepository.deleteByFeedId(feedId);
    }

    @Override
    public void delete(FeedLike feedLike) {
        jpaRepository.deleteById(feedLike.getId());
    }

    private FeedLikeJpaEntity toJpa(FeedLike like) {
        return new FeedLikeJpaEntity(
                like.getId(),
                like.getFeedId(),
                like.getFanId(),
                like.getArtistMemberId(),
                like.getArtistId(),
                like.getCreatedAt()
        );
    }

    private FeedLike toDomain(FeedLikeJpaEntity e) {
        return FeedLike.reconstruct(
                e.getId(),
                e.getFeedId(),
                e.getFanId(),
                e.getArtistMemberId(),
                e.getArtistId(),
                e.getCreatedAt()
        );
    }
}