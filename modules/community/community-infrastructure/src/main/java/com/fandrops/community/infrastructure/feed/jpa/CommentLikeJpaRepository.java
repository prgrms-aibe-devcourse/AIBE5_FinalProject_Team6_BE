package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface CommentLikeJpaRepository extends JpaRepository<CommentLikeJpaEntity, Long> {

    Optional<CommentLikeJpaEntity> findByCommentIdAndFanId(Long commentId, Long fanId);

    boolean existsByCommentIdAndFanId(Long commentId, Long fanId);

    // 댓글 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    @Modifying(clearAutomatically = true)
    @Transactional
    void deleteByCommentId(Long commentId);
}