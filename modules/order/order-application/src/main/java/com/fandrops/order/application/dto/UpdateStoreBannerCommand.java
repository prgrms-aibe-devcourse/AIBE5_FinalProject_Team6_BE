package com.fandrops.order.application.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class UpdateStoreBannerCommand {
    private final Long bannerId;
    private final String title;
    private final String imageUrl;
    private final String landingUrl;
    private final Integer exposureOrder;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Long productId;
}
