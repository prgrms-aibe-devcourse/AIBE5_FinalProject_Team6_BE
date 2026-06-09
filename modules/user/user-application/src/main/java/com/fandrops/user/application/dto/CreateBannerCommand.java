package com.fandrops.user.application.dto;

import java.time.LocalDateTime;

public record CreateBannerCommand(
        String title,
        String imageUrl,
        String landingUrl,
        int exposureOrder,
        LocalDateTime startAt,
        LocalDateTime endAt
) {}