package com.fandrops.community.application.schedule;

import java.util.List;

public record NoticeListResult(
        List<NoticeResult> items,
        String nextCursor,
        boolean hasMore
) {}
