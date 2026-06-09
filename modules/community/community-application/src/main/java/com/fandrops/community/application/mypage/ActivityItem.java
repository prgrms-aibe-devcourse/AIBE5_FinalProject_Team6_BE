package com.fandrops.community.application.mypage;

import java.time.OffsetDateTime;

public record ActivityItem(
        ActivityType type,
        Long id,
        Long feedId,
        Long artistId,
        String content,
        OffsetDateTime createdAt
) {}