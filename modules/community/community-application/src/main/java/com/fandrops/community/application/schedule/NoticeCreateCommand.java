package com.fandrops.community.application.schedule;

import java.time.OffsetDateTime;
import java.util.List;

public record NoticeCreateCommand(
        Long artistId,
        Long artistMemberId,
        String title,
        String content,
        List<String> imageUrls,
        OffsetDateTime scheduledAt,
        boolean autoSyncCalendar,
        String calendarType           // DROP | EVENT | LIVE, autoSyncCalendar=true 시만 사용
) {}
