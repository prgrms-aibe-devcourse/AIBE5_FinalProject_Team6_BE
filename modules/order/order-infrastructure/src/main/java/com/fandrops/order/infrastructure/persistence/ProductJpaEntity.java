package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private ProductJpaEntity(Long artistId, String name, BigDecimal price, ProductStatus status) {
        this.artistId = artistId;
        this.name = name;
        this.price = price;
        this.status = status;
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(
                product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus());
    }

    public static ProductJpaEntity fromWithId(Product product) {
        ProductJpaEntity entity = new ProductJpaEntity(
                product.getArtistId(), product.getName(),
                product.getPrice(), product.getStatus());
        entity.id = product.getId();
        return entity;
    }

    public Product toDomain() {
        return Product.of(id, artistId, name, price, status, updatedAt);
    }
}
