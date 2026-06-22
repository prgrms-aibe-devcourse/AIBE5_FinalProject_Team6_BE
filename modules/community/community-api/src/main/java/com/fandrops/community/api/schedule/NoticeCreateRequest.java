package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record NoticeCreateRequest(
        @NotBlank String title,
        String content,
        @Size(max = 10) List<String> imageUrls,
        OffsetDateTime scheduledAt,
        boolean autoSyncCalendar,     // true 시 calendarType 타입 캘린더 항목 자동 생성
        String calendarType           // DROP | EVENT | LIVE (autoSyncCalendar=true 시 필수)
) {
    public NoticeCreateRequest {
        imageUrls = imageUrls != null ? imageUrls : List.of();
    }
}
