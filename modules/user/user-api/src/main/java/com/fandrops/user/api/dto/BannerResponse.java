package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.BannerResult;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record BannerResponse(
        Long id,
        Long agencyId,
        Long productId,
        String title,
        String imageUrl,
        String landingUrl,
        int exposureOrder,
        boolean isActive,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime startAt,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul") LocalDateTime endAt
) {
    public static BannerResponse from(BannerResult result) {
        return new BannerResponse(
                result.id(),
                result.agencyId(),
                result.productId(),
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
