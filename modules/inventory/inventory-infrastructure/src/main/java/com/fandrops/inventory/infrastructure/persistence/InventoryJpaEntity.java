package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.Inventory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private InventoryJpaEntity(Long id, Long productId, int totalQty, int reservedQty, int availableQty, int version) {
        this.id = id;
        this.productId = productId;
        this.totalQty = totalQty;
        this.reservedQty = reservedQty;
        this.availableQty = availableQty;
        this.version = version;
        this.updatedAt = LocalDateTime.now();
    }

    public static InventoryJpaEntity from(Inventory inventory) {
        return new InventoryJpaEntity(
            inventory.getId(),
            inventory.getProductId(),
            inventory.getTotalQty(),
            inventory.getReservedQty(),
            inventory.getAvailableQty(),
            inventory.getVersion()
        );
    }

    public Inventory toDomain() {
        return Inventory.reconstitute(id, productId, totalQty, reservedQty, availableQty, version);
    }
}
