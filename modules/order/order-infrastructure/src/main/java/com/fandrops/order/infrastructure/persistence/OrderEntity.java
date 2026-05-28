package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** orders 테이블 JPA 엔티티. ORDER는 SQL 예약어라 테이블명을 orders로 지정. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    // ERD 미포함이나 API 스펙 필수 — POST /orders 응답의 orderPaymentKey
    @Column(name = "order_payment_key", nullable = false, unique = true, length = 40)
    private String orderPaymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public OrderEntity(Long fanId, String idempotencyKey, String orderPaymentKey,
                       OrderStatus status, BigDecimal totalAmount) {
        this.fanId = fanId;
        this.idempotencyKey = idempotencyKey;
        this.orderPaymentKey = orderPaymentKey;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public void addItem(OrderItemEntity item) {
        this.items.add(item);
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
