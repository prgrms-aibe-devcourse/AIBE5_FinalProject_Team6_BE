package com.fandrops.order.infrastructure.persistence;

import com.fandrops.order.domain.CartItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cart_item",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id", nullable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    public CartItemJpaEntity(Long cartId, Long productId, int quantity, LocalDateTime addedAt) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
        this.addedAt = addedAt;
    }

    public static CartItemJpaEntity from(CartItem item) {
        return new CartItemJpaEntity(
                item.getCartId(), item.getProductId(), item.getQuantity(), item.getAddedAt());
    }

    public static CartItemJpaEntity fromWithId(CartItem item) {
        CartItemJpaEntity entity = new CartItemJpaEntity(
                item.getCartId(), item.getProductId(), item.getQuantity(), item.getAddedAt());
        entity.id = item.getId();
        return entity;
    }

    public CartItem toDomain() {
        return CartItem.of(id, cartId, productId, quantity, addedAt);
    }
}
