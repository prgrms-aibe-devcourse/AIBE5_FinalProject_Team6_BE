package com.fandrops.user.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class Banner {

    private final Long id;
    private final BannerType bannerType;
    private String title;
    private String imageUrl;
    private String landingUrl;
    private int exposureOrder;
    private boolean isActive;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private Banner(Builder builder) {
        this.id = builder.id;
        this.bannerType = builder.bannerType;
        this.title = builder.title;
        this.imageUrl = builder.imageUrl;
        this.landingUrl = builder.landingUrl;
        this.exposureOrder = builder.exposureOrder;
        this.isActive = builder.isActive;
        this.startAt = builder.startAt;
        this.endAt = builder.endAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** PATCH용 — null 필드는 변경하지 않는다 */
    public void update(String title, String imageUrl, String landingUrl,
                       Integer exposureOrder, Boolean isActive,
                       LocalDateTime startAt, LocalDateTime endAt) {
        if (title != null) this.title = title;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (landingUrl != null) this.landingUrl = landingUrl;
        if (exposureOrder != null) this.exposureOrder = exposureOrder;
        if (isActive != null) this.isActive = isActive;
        if (startAt != null) this.startAt = startAt;
        if (endAt != null) this.endAt = endAt;
    }

    /** DELETE soft delete */
    public void deactivate() {
        this.isActive = false;
    }

    public Long getId() { return id; }
    public BannerType getBannerType() { return bannerType; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
    public String getLandingUrl() { return landingUrl; }
    public int getExposureOrder() { return exposureOrder; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }

    public static class Builder {
        private Long id;
        private BannerType bannerType = BannerType.MAIN;
        private String title;
        private String imageUrl;
        private String landingUrl;
        private int exposureOrder = 0;
        private boolean isActive = true;
        private LocalDateTime startAt;
        private LocalDateTime endAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder bannerType(BannerType bannerType) { this.bannerType = bannerType; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder imageUrl(String imageUrl) { this.imageUrl = imageUrl; return this; }
        public Builder landingUrl(String landingUrl) { this.landingUrl = landingUrl; return this; }
        public Builder exposureOrder(int exposureOrder) { this.exposureOrder = exposureOrder; return this; }
        public Builder isActive(boolean isActive) { this.isActive = isActive; return this; }
        public Builder startAt(LocalDateTime startAt) { this.startAt = startAt; return this; }
        public Builder endAt(LocalDateTime endAt) { this.endAt = endAt; return this; }

        public Banner build() {
            Objects.requireNonNull(title, "title은 필수입니다");
            Objects.requireNonNull(imageUrl, "imageUrl은 필수입니다");
            Objects.requireNonNull(landingUrl, "landingUrl은 필수입니다");
            return new Banner(this);
        }
    }
}