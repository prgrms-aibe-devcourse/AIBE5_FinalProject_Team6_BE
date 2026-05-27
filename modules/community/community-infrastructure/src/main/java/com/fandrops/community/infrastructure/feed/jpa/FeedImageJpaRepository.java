package com.fandrops.community.infrastructure.feed.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedImageJpaRepository extends JpaRepository<FeedImageJpaEntity, Long> {

    // 등록 순서 = 표시 순서 (ERD §5.1)
    List<FeedImageJpaEntity> findByFeedIdOrderByCreatedAt(Long feedId);

    void deleteByFeedId(Long feedId);
}