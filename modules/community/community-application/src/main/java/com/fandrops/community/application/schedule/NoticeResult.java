package com.fandrops.community.application.schedule;

import com.fandrops.community.domain.schedule.ArtistScheduleType;

import java.time.OffsetDateTime;
import java.util.List;

public record NoticeResult(
        Long id,
        ArtistScheduleType type,
        String title,
        String content,
        List<String> imageUrls,
        OffsetDateTime scheduledAt
) {}
