package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.Banner;
import com.fandrops.user.domain.BannerType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "banner")
public class BannerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "banner_type", nullable = false)
    private BannerType bannerType;

    @Column(nullable = false)
    private String title;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "landing_url", nullable = false)
    private String landingUrl;

    @Column(name = "exposure_order", nullable = false)
    private int exposureOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "agency_id")
    private Long agencyId;

    @Column(name = "product_id")
    private Long productId;

    protected BannerJpaEntity() {}

    public static BannerJpaEntity from(Banner domain) {
        BannerJpaEntity entity = new BannerJpaEntity();
        entity.id = domain.getId();
        entity.bannerType = domain.getBannerType();
        entity.title = domain.getTitle();
        entity.imageUrl = domain.getImageUrl();
        entity.landingUrl = domain.getLandingUrl();
        entity.exposureOrder = domain.getExposureOrder();
        entity.isActive = domain.isActive();
        entity.startAt = domain.getStartAt();
        entity.endAt = domain.getEndAt();
        entity.agencyId = domain.getAgencyId();
        entity.productId = domain.getProductId();
        return entity;
    }

    public Banner toDomain() {
        return Banner.builder()
                .id(id)
                .bannerType(bannerType)
                .title(title)
                .imageUrl(imageUrl)
                .landingUrl(landingUrl)
                .exposureOrder(exposureOrder)
                .isActive(isActive)
                .startAt(startAt)
                .endAt(endAt)
                .agencyId(agencyId)
                .productId(productId)
                .build();
    }
}
