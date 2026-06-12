package com.fandrops.community.api.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;

public record LiveCreateRequest(
        @NotBlank String title,
        @NotNull OffsetDateTime scheduledAt,
        @Pattern(regexp = "^https://www\\.youtube\\.com/embed/.*", message = "liveUrl은 유튜브 임베드 URL이어야 합니다.")
        String liveUrl
) {}