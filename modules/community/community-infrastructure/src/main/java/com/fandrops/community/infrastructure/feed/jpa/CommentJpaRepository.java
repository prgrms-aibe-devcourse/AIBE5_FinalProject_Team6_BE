package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, Long> {

    // 최상위 댓글 커서 페이징 (parent_id IS NULL)
    List<CommentJpaEntity> findByFeedIdAndParentIdIsNullOrderByIdAsc(Long feedId, Pageable pageable);

    List<CommentJpaEntity> findByFeedIdAndParentIdIsNullAndIdGreaterThanOrderByIdAsc(Long feedId, Long id, Pageable pageable);

    // 대댓글 전체 조회 (소량이므로 커서 없이)
    List<CommentJpaEntity> findByParentIdOrderByIdAsc(Long parentId);

    // /fans/me/activities 커서 페이징
    List<CommentJpaEntity> findByFanIdOrderByIdDesc(Long fanId, Pageable pageable);

    List<CommentJpaEntity> findByFanIdAndIdLessThanOrderByIdDesc(Long fanId, Long id, Pageable pageable);

    // 피드 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    void deleteByFeedId(Long feedId);
}