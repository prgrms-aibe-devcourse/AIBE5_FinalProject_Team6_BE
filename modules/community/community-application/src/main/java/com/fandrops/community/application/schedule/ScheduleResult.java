package com.fandrops.community.application.schedule;

import com.fandrops.community.domain.schedule.ArtistScheduleType;

import java.time.OffsetDateTime;

public record ScheduleResult(
        Long id,
        ArtistScheduleType type,
        String title,
        OffsetDateTime startTime,
        String liveUrl
) {}