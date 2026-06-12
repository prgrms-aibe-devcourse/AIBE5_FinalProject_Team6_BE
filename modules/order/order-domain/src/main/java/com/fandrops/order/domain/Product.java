package com.fandrops.order.domain;

import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class Product {

    private Long id;
    private Long artistId;
    private String name;
    private BigDecimal price;
    private ProductStatus status;
    private LocalDateTime dropsStartAt;
    private LocalDateTime dropsEndAt;
    private LocalDateTime updatedAt;

    private Product() {}

    public static Product createRegular(Long artistId, String name, BigDecimal price) {
        Product p = new Product();
        p.artistId = artistId;
        p.name = name;
        p.price = price;
        p.status = ProductStatus.ON_SALE;
        return p;
    }

    public static Product createDrops(Long artistId, String name, BigDecimal price,
                                      LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        if (dropsStartAt == null || dropsEndAt == null) {
            throw new IllegalArgumentException("드롭스 상품은 dropsStartAt, dropsEndAt 모두 필수입니다.");
        }
        if (!dropsStartAt.isBefore(dropsEndAt)) {
            throw new IllegalArgumentException("dropsStartAt은 dropsEndAt보다 이전이어야 합니다.");
        }
        Product p = new Product();
        p.artistId = artistId;
        p.name = name;
        p.price = price;
        p.status = ProductStatus.ON_SALE;
        p.dropsStartAt = dropsStartAt;
        p.dropsEndAt = dropsEndAt;
        return p;
    }

    public static Product of(Long id, Long artistId, String name, BigDecimal price,
                             ProductStatus status, LocalDateTime dropsStartAt,
                             LocalDateTime dropsEndAt, LocalDateTime updatedAt) {
        Product p = new Product();
        p.id = id;
        p.artistId = artistId;
        p.name = name;
        p.price = price;
        p.status = status;
        p.dropsStartAt = dropsStartAt;
        p.dropsEndAt = dropsEndAt;
        p.updatedAt = updatedAt;
        return p;
    }

    public boolean isDrops() {
        return dropsStartAt != null && dropsEndAt != null;
    }

    public void update(String name, BigDecimal price, ProductStatus status,
                       LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        // 한쪽만 전달되면 zombie product 방지 — 도메인 계층 방어
        if ((dropsStartAt == null) != (dropsEndAt == null)) {
            throw new IllegalArgumentException("drops 기간은 양쪽 모두 입력하거나 모두 null이어야 합니다.");
        }
        if (name != null) this.name = name;
        if (price != null) this.price = price;
        if (status != null) this.status = status;
        if (dropsStartAt != null) this.dropsStartAt = dropsStartAt;
        if (dropsEndAt != null) this.dropsEndAt = dropsEndAt;
        if (this.dropsStartAt != null && this.dropsEndAt != null
                && !this.dropsStartAt.isBefore(this.dropsEndAt)) {
            throw new IllegalArgumentException("dropsStartAt은 dropsEndAt보다 이전이어야 합니다.");
        }
    }

    public void markSoldOut() { this.status = ProductStatus.SOLD_OUT; }
    public void markOnSale()  { this.status = ProductStatus.ON_SALE; }
}
