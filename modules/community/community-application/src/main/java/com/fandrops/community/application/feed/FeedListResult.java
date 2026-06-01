package com.fandrops.community.application.feed;

import java.util.List;

public record FeedListResult(
        List<FeedResult> items,
        String nextCursor,
        boolean hasMore
) {}