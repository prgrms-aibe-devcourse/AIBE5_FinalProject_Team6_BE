package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.domain.feed.FeedImage;
import com.fandrops.community.domain.feed.repository.FeedImageRepository;
import com.fandrops.community.infrastructure.feed.jpa.FeedImageJpaEntity;
import com.fandrops.community.infrastructure.feed.jpa.FeedImageJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeedImageRepositoryAdapter implements FeedImageRepository {

    private final FeedImageJpaRepository jpaRepository;

    public FeedImageRepositoryAdapter(FeedImageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<FeedImage> saveAll(List<FeedImage> images) {
        List<FeedImageJpaEntity> entities = images.stream().map(this::toJpa).toList();
        return jpaRepository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public List<FeedImage> findByFeedIdOrderByCreatedAt(Long feedId) {
        return jpaRepository.findByFeedIdOrderByCreatedAt(feedId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<FeedImage> findByFeedIdInOrderByCreatedAt(List<Long> feedIds) {
        return jpaRepository.findByFeedIdInOrderByCreatedAt(feedIds)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByFeedId(Long feedId) {
        jpaRepository.deleteByFeedId(feedId);
    }

    private FeedImageJpaEntity toJpa(FeedImage image) {
        return new FeedImageJpaEntity(
                image.getId(),
                image.getFeedId(),
                image.getImageUrl(),
                image.getCreatedAt()
        );
    }

    private FeedImage toDomain(FeedImageJpaEntity e) {
        return FeedImage.reconstruct(e.getId(), e.getFeedId(), e.getImageUrl(), e.getCreatedAt());
    }
}