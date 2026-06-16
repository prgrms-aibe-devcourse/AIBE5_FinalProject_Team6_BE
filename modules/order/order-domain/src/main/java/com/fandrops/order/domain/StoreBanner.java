package com.fandrops.order.domain;

import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class StoreBanner {

    private final Long id;
    private final String title;
    private final String imageUrl;
    private final String landingUrl;
    private final int exposureOrder;
    private final boolean active;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Long productId;

    private StoreBanner(Long id, String title, String imageUrl, String landingUrl,
                        int exposureOrder, boolean active, LocalDateTime startAt,
                        LocalDateTime endAt, Long productId) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.landingUrl = landingUrl;
        this.exposureOrder = exposureOrder;
        this.active = active;
        this.startAt = startAt;
        this.endAt = endAt;
        this.productId = productId;
    }

    public static StoreBanner create(String title, String imageUrl, String landingUrl,
                                     int exposureOrder, LocalDateTime startAt,
                                     LocalDateTime endAt, Long productId) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("배너 제목은 필수입니다.");
        if (imageUrl == null || imageUrl.isBlank()) throw new IllegalArgumentException("이미지 URL은 필수입니다.");
        if (landingUrl == null || landingUrl.isBlank()) throw new IllegalArgumentException("랜딩 URL은 필수입니다.");
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("startAt은 endAt보다 이전이어야 합니다.");
        }
        return new StoreBanner(null, title, imageUrl, landingUrl, exposureOrder, true,
                startAt, endAt, productId);
    }

    public static StoreBanner of(Long id, String title, String imageUrl, String landingUrl,
                                  int exposureOrder, boolean active, LocalDateTime startAt,
                                  LocalDateTime endAt, Long productId) {
        return new StoreBanner(id, title, imageUrl, landingUrl, exposureOrder, active,
                startAt, endAt, productId);
    }

    public BannerStatus computeStatus(LocalDateTime now) {
        if (!active) return BannerStatus.INACTIVE;
        if (endAt != null && now.isAfter(endAt)) return BannerStatus.INACTIVE;
        if (startAt != null && now.isBefore(startAt)) return BannerStatus.WAITING;
        return BannerStatus.ACTIVE;
    }

    public StoreBanner deactivate() {
        return new StoreBanner(id, title, imageUrl, landingUrl, exposureOrder, false,
                startAt, endAt, productId);
    }

    public StoreBanner update(String title, String imageUrl, String landingUrl,
                              Integer exposureOrder, LocalDateTime startAt,
                              LocalDateTime endAt, Long productId) {
        String newTitle = title != null ? title : this.title;
        String newImageUrl = imageUrl != null ? imageUrl : this.imageUrl;
        String newLandingUrl = landingUrl != null ? landingUrl : this.landingUrl;
        int newExposureOrder = exposureOrder != null ? exposureOrder : this.exposureOrder;
        LocalDateTime newStartAt = startAt != null ? startAt : this.startAt;
        LocalDateTime newEndAt = endAt != null ? endAt : this.endAt;
        Long newProductId = productId != null ? productId : this.productId;
        if (newStartAt != null && newEndAt != null && !newStartAt.isBefore(newEndAt)) {
            throw new IllegalArgumentException("startAt은 endAt보다 이전이어야 합니다.");
        }
        return new StoreBanner(id, newTitle, newImageUrl, newLandingUrl, newExposureOrder,
                active, newStartAt, newEndAt, newProductId);
    }
}
