package com.fandrops.community.application.schedule;

import java.time.OffsetDateTime;

public record LiveCreateCommand(
        Long artistId,
        Long artistMemberId,
        String title,
        OffsetDateTime scheduledAt,
        String liveUrl
) {}