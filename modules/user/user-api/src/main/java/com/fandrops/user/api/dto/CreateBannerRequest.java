package com.fandrops.user.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public record CreateBannerRequest(
        @NotBlank String title,
        @NotBlank String imageUrl,
        @NotBlank String landingUrl,
        @Min(0) int exposureOrder,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}