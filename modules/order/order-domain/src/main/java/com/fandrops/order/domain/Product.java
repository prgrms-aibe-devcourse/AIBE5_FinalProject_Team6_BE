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

    public void update(String name, BigDecimal price, ProductStatus status) {
        if (name != null) this.name = name;
        if (price != null) this.price = price;
        if (status != null) this.status = status;
    }

    public void markSoldOut() { this.status = ProductStatus.SOLD_OUT; }
    public void markOnSale()  { this.status = ProductStatus.ON_SALE; }
}
