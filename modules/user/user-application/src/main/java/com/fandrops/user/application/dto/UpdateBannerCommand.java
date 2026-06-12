package com.fandrops.user.application.dto;

import java.time.LocalDateTime;
import java.util.Optional;

public record UpdateBannerCommand(
        String title,
        String imageUrl,
        String landingUrl,
        Integer exposureOrder,
        Boolean isActive,
        Optional<LocalDateTime> startAt,
        Optional<LocalDateTime> endAt
) {}