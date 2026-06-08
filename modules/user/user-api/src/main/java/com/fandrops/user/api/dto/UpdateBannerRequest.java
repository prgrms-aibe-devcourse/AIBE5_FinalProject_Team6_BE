package com.fandrops.user.api.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public record UpdateBannerRequest(
        String title,
        String imageUrl,
        String landingUrl,
        @Min(0) Integer exposureOrder,
        Boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}