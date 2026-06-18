package com.fandrops.user.application.dto;

import com.fandrops.user.domain.Banner;

import java.time.LocalDateTime;

public record BannerResult(
        Long id,
        Long agencyId,
        String title,
        String imageUrl,
        String landingUrl,
        int exposureOrder,
        boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static BannerResult from(Banner banner) {
        return new BannerResult(
                banner.getId(),
                banner.getAgencyId(),
                banner.getTitle(),
                banner.getImageUrl(),
                banner.getLandingUrl(),
                banner.getExposureOrder(),
                banner.isActive(),
                banner.getStartAt(),
                banner.getEndAt()
        );
    }
}