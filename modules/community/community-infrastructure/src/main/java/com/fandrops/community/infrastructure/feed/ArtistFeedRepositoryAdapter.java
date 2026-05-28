package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.domain.feed.ArtistFeed;
import com.fandrops.community.domain.feed.repository.ArtistFeedRepository;
import com.fandrops.community.infrastructure.feed.jpa.ArtistFeedJpaEntity;
import com.fandrops.community.infrastructure.feed.jpa.ArtistFeedJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ArtistFeedRepositoryAdapter implements ArtistFeedRepository {

    private final ArtistFeedJpaRepository jpaRepository;

    public ArtistFeedRepositoryAdapter(ArtistFeedJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ArtistFeed save(ArtistFeed feed) {
        ArtistFeedJpaEntity entity = toJpa(feed);
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<ArtistFeed> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ArtistFeed> findByArtistId(Long artistId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<ArtistFeedJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByArtistIdOrderByIdDesc(artistId, page)
                : jpaRepository.findByArtistIdAndIdLessThanOrderByIdDesc(artistId, cursorId, page);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void delete(ArtistFeed feed) {
        jpaRepository.deleteById(feed.getId());
    }

    @Override
    public void incrementLikeCount(Long feedId) {
        jpaRepository.incrementLikeCount(feedId);
    }

    @Override
    public void decrementLikeCount(Long feedId) {
        jpaRepository.decrementLikeCount(feedId);
    }

    @Override
    public void incrementCommentCount(Long feedId) {
        jpaRepository.incrementCommentCount(feedId);
    }

    @Override
    public void decrementCommentCount(Long feedId) {
        jpaRepository.decrementCommentCount(feedId);
    }

    private ArtistFeedJpaEntity toJpa(ArtistFeed feed) {
        return new ArtistFeedJpaEntity(
                feed.getId(),
                feed.getArtistId(),
                feed.getArtistMemberId(),
                feed.getContent(),
                feed.getLikeCount(),
                feed.getCommentCount(),
                feed.getCreatedAt()
        );
    }

    private ArtistFeed toDomain(ArtistFeedJpaEntity e) {
        return ArtistFeed.reconstruct(
                e.getId(),
                e.getArtistId(),
                e.getArtistMemberId(),
                e.getContent(),
                e.getLikeCount(),
                e.getCommentCount(),
                e.getCreatedAt()
        );
    }
}