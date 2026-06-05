package com.fandrops.community.application.schedule;

import java.time.OffsetDateTime;

public record EventCreateCommand(
        Long artistId,
        Long artistMemberId,
        String title,
        String type,          // "DROP" | "LIVE" | "EVENT" | "NOTICE" — 서비스에서 ArtistScheduleType으로 변환
        OffsetDateTime scheduledAt
) {}