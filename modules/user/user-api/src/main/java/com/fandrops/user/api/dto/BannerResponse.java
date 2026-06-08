package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.BannerResult;

import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        String title,
        String imageUrl,
        String landingUrl,
        int exposureOrder,
        boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static BannerResponse from(BannerResult result) {
        return new BannerResponse(
                result.id(),
                result.title(),
                result.imageUrl(),
                result.landingUrl(),
                result.exposureOrder(),
                result.isActive(),
                result.startAt(),
                result.endAt()
        );
    }
}
