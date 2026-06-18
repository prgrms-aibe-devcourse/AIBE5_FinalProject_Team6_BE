package com.fandrops.community.infrastructure.feed;

import com.fandrops.community.domain.feed.Comment;
import com.fandrops.community.domain.feed.repository.CommentRepository;
import com.fandrops.community.infrastructure.feed.jpa.CommentJpaEntity;
import com.fandrops.community.infrastructure.feed.jpa.CommentJpaRepository;
import com.fandrops.community.infrastructure.feed.jpa.CommentLikeJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CommentRepositoryAdapter implements CommentRepository {

    private final CommentJpaRepository jpaRepository;
    private final CommentLikeJpaRepository commentLikeJpaRepository;

    public CommentRepositoryAdapter(CommentJpaRepository jpaRepository,
                                    CommentLikeJpaRepository commentLikeJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.commentLikeJpaRepository = commentLikeJpaRepository;
    }

    @Override
    public Comment save(Comment comment) {
        return toDomain(jpaRepository.save(toJpa(comment)));
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Comment> findTopLevelByFeedId(Long feedId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<CommentJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByFeedIdAndParentIdIsNullOrderByIdAsc(feedId, page)
                : jpaRepository.findByFeedIdAndParentIdIsNullAndIdGreaterThanOrderByIdAsc(feedId, cursorId, page);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Comment> findRepliesByParentId(Long parentId) {
        return jpaRepository.findByParentIdOrderByIdAsc(parentId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Comment> findRepliesByParentIds(List<Long> parentIds) {
        if (parentIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByParentIdInOrderByParentIdAscIdAsc(parentIds)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Comment> findByFanId(Long fanId, Long cursorId, int size) {
        PageRequest page = PageRequest.of(0, size);
        List<CommentJpaEntity> entities = (cursorId == null)
                ? jpaRepository.findByFanIdOrderByIdDesc(fanId, page)
                : jpaRepository.findByFanIdAndIdLessThanOrderByIdDesc(fanId, cursorId, page);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByFeedId(Long feedId) {
        jpaRepository.deleteByFeedId(feedId);
    }

    @Override
    public void delete(Comment comment) {
        commentLikeJpaRepository.deleteByCommentId(comment.getId());
        jpaRepository.deleteById(comment.getId());
    }

    private CommentJpaEntity toJpa(Comment comment) {
        return new CommentJpaEntity(
                comment.getId(),
                comment.getFeedId(),
                comment.getArtistId(),
                comment.getFanId(),
                comment.getArtistMemberId(),
                comment.getParentId(),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }

    private Comment toDomain(CommentJpaEntity e) {
        return Comment.reconstruct(
                e.getId(),
                e.getFeedId(),
                e.getArtistId(),
                e.getFanId(),
                e.getArtistMemberId(),
                e.getParentId(),
                e.getContent(),
                e.getCreatedAt()
        );
    }
}