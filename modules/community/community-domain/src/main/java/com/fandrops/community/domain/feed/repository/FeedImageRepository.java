package com.fandrops.community.domain.feed.repository;

import com.fandrops.community.domain.feed.FeedImage;

import java.util.List;

public interface FeedImageRepository {

    List<FeedImage> saveAll(List<FeedImage> images);

    // createdAt 오름차순 정렬 — 등록 순서가 표시 순서 (ERD §5.1)
    List<FeedImage> findByFeedIdOrderByCreatedAt(Long feedId);

    void deleteByFeedId(Long feedId);
}