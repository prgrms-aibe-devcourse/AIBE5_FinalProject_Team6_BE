package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record EventCreateRequest(
        @NotBlank String title,
        @NotBlank String type,       // "DROP" | "LIVE" | "EVENT" | "NOTICE"
        @NotNull OffsetDateTime scheduledAt
) {}