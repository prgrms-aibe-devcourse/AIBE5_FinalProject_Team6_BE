package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.Comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(Long id);

    // 최상위 댓글 커서 페이징
    List<Comment> findTopLevelByFeedId(Long feedId, Long cursorId, int size);

    // 대댓글 전체 조회 (대댓글은 보통 소량이므로 커서 없이 조회)
    List<Comment> findRepliesByParentId(Long parentId);

    // /fans/me/activities — 팬 댓글 이력 커서 페이징
    List<Comment> findByFanId(Long fanId, Long cursorId, int size);

    // 피드 삭제 시 연계 삭제 (data-lifecycle.md §3.5)
    void deleteByFeedId(Long feedId);

    void delete(Comment comment);
}