package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.domain.feed.CommentLike;
import com.fandrops.community.domain.feed.repository.CommentLikeRepository;
import com.fandrops.community.infrastructure.feed.jpa.CommentLikeJpaEntity;
import com.fandrops.community.infrastructure.feed.jpa.CommentLikeJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CommentLikeRepositoryAdapter implements CommentLikeRepository {

    private final CommentLikeJpaRepository jpaRepository;

    public CommentLikeRepositoryAdapter(CommentLikeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CommentLike save(CommentLike commentLike) {
        return toDomain(jpaRepository.save(toJpa(commentLike)));
    }

    @Override
    public Optional<CommentLike> findByCommentIdAndFanId(Long commentId, Long fanId) {
        return jpaRepository.findByCommentIdAndFanId(commentId, fanId).map(this::toDomain);
    }

    @Override
    public boolean existsByCommentIdAndFanId(Long commentId, Long fanId) {
        return jpaRepository.existsByCommentIdAndFanId(commentId, fanId);
    }

    @Override
    public void deleteByCommentId(Long commentId) {
        jpaRepository.deleteByCommentId(commentId);
    }

    @Override
    public void deleteByFeedId(Long feedId) {
        jpaRepository.deleteByFeedId(feedId);
    }

    @Override
    public void delete(CommentLike commentLike) {
        jpaRepository.deleteById(commentLike.getId());
    }

    private CommentLikeJpaEntity toJpa(CommentLike like) {
        return new CommentLikeJpaEntity(
                like.getId(),
                like.getCommentId(),
                like.getFanId(),
                like.getCreatedAt()
        );
    }

    private CommentLike toDomain(CommentLikeJpaEntity e) {
        return CommentLike.reconstruct(e.getId(), e.getCommentId(), e.getFanId(), e.getCreatedAt());
    }
}