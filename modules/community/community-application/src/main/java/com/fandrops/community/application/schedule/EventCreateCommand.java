package com.fandrops.community.application.schedule;

import java.time.OffsetDateTime;

public record EventCreateCommand(
        Long artistId,
        Long artistMemberId,
        String title,
        String type,          // "DROP" | "LIVE" | "EVENT" | "NOTICE" — 서비스에서 ArtistScheduleType으로 변환
        OffsetDateTime scheduledAt,
        String externalTicketUrl  // EVENT 타입 전용, 나머지 타입은 null
) {}