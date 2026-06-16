package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.StoreBanner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "StoreBannerEntity")
@Table(name = "banner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoreBannerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "banner_type", nullable = false, length = 20)
    private String bannerType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "landing_url", nullable = false, length = 500)
    private String landingUrl;

    @Column(name = "exposure_order", nullable = false)
    private int exposureOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "product_id")
    private Long productId;

    private StoreBannerJpaEntity(String bannerType, String title, String imageUrl,
                                  String landingUrl, int exposureOrder, boolean isActive,
                                  LocalDateTime startAt, LocalDateTime endAt, Long productId) {
        this.bannerType = bannerType;
        this.title = title;
        this.imageUrl = imageUrl;
        this.landingUrl = landingUrl;
        this.exposureOrder = exposureOrder;
        this.isActive = isActive;
        this.startAt = startAt;
        this.endAt = endAt;
        this.productId = productId;
    }

    public static StoreBannerJpaEntity from(StoreBanner banner) {
        return new StoreBannerJpaEntity("STORE", banner.getTitle(), banner.getImageUrl(),
                banner.getLandingUrl(), banner.getExposureOrder(), banner.isActive(),
                banner.getStartAt(), banner.getEndAt(), banner.getProductId());
    }

    public static StoreBannerJpaEntity fromWithId(StoreBanner banner) {
        StoreBannerJpaEntity entity = new StoreBannerJpaEntity("STORE", banner.getTitle(),
                banner.getImageUrl(), banner.getLandingUrl(), banner.getExposureOrder(),
                banner.isActive(), banner.getStartAt(), banner.getEndAt(), banner.getProductId());
        entity.id = banner.getId();
        return entity;
    }

    public StoreBanner toDomain() {
        return StoreBanner.of(id, title, imageUrl, landingUrl, exposureOrder,
                isActive, startAt, endAt, productId);
    }
}
