package com.fandrops.community.application.feed;

import java.time.OffsetDateTime;
import java.util.List;

public record FeedResult(
        Long id,
        Long artistId,
        Long artistMemberId,
        String content,
        int likeCount,
        int commentCount,
        List<String> imageUrls,
        OffsetDateTime createdAt,
        boolean isLiked
) {}