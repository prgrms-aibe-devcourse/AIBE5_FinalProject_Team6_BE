package com.fandrops.community.application.mypage;

import java.util.List;

public record ActivityListResult(
        List<ActivityItem> items,
        String nextCursor,
        boolean hasMore
) {}