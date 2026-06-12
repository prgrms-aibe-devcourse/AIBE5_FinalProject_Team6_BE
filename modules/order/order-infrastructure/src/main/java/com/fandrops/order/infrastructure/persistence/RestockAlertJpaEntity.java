package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.RestockAlert;
import com.fandrops.order.domain.RestockAlertStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "restock_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestockAlertJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestockAlertStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private RestockAlertJpaEntity(Long fanId, Long productId, RestockAlertStatus status) {
        this.fanId = fanId;
        this.productId = productId;
        this.status = status;
    }

    public static RestockAlertJpaEntity from(RestockAlert alert) {
        return new RestockAlertJpaEntity(alert.getFanId(), alert.getProductId(), alert.getStatus());
    }

    public static RestockAlertJpaEntity fromWithId(RestockAlert alert) {
        RestockAlertJpaEntity entity = new RestockAlertJpaEntity(
                alert.getFanId(), alert.getProductId(), alert.getStatus());
        entity.id = alert.getId();
        return entity;
    }

    public RestockAlert toDomain() {
        return RestockAlert.of(id, fanId, productId, status, createdAt);
    }
}
