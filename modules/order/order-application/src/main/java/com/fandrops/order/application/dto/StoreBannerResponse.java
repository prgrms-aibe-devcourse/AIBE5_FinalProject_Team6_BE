package com.fandrops.order.application.dto;

import com.fandrops.order.domain.StoreBanner;
import java.time.LocalDateTime;

public class StoreBannerResponse {

    private final Long id;
    private final String title;
    private final String imageUrl;
    private final String landingUrl;
    private final int exposureOrder;
    private final String status;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Long productId;

    private StoreBannerResponse(Long id, String title, String imageUrl, String landingUrl,
                                 int exposureOrder, String status, LocalDateTime startAt,
                                 LocalDateTime endAt, Long productId) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.landingUrl = landingUrl;
        this.exposureOrder = exposureOrder;
        this.status = status;
        this.startAt = startAt;
        this.endAt = endAt;
        this.productId = productId;
    }

    public static StoreBannerResponse from(StoreBanner banner) {
        return new StoreBannerResponse(
                banner.getId(), banner.getTitle(), banner.getImageUrl(), banner.getLandingUrl(),
                banner.getExposureOrder(), banner.computeStatus(LocalDateTime.now()).name(),
                banner.getStartAt(), banner.getEndAt(), banner.getProductId());
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
    public String getLandingUrl() { return landingUrl; }
    public int getExposureOrder() { return exposureOrder; }
    public String getStatus() { return status; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public Long getProductId() { return productId; }
}
