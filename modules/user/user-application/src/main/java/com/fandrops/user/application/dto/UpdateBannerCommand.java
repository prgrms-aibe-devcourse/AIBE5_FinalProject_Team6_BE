package com.fandrops.user.application.dto;

import java.time.LocalDateTime;

public record UpdateBannerCommand(
        String title,
        String imageUrl,
        String landingUrl,
        Integer exposureOrder,
        Boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}