package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record EventCreateRequest(
        @NotBlank String title,
        @NotBlank String type,       // "DROP" | "LIVE" | "EVENT" | "NOTICE"
        @NotNull OffsetDateTime scheduledAt,
        @Size(max = 2048, message = "externalTicketUrl은 2048자 이내여야 합니다.")
        @Pattern(regexp = "^https?://.+", message = "externalTicketUrl은 http(s)://로 시작해야 합니다.")
        String externalTicketUrl,    // EVENT 타입 전용 (선택)
        Long linkNoticeId            // optional: 연결할 NOTICE 타입 schedule ID
) {}