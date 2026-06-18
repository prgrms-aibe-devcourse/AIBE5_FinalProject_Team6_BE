package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public record EventCreateRequest(
        @NotBlank String title,
        @NotBlank String type,       // "DROP" | "LIVE" | "EVENT" | "NOTICE"
        @NotNull OffsetDateTime scheduledAt,
        @Pattern(regexp = "^https?://.+", message = "externalTicketUrl은 http(s)://로 시작해야 합니다.")
        String externalTicketUrl     // EVENT 타입 전용 (선택)
) {}