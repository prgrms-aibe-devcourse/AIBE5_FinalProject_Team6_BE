package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private InventoryChangeType changeType;

    @Column(name = "qty_delta", nullable = false)
    private int qtyDelta;

    @Column(name = "qty_before", nullable = false)
    private int qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private int qtyAfter;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false)
    private InventoryRefType refType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    private InventoryHistoryJpaEntity(Long id, Long inventoryId, InventoryChangeType changeType,
                                     int qtyDelta, int qtyBefore, int qtyAfter, Long referenceId, InventoryRefType refType) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.changeType = changeType;
        this.qtyDelta = qtyDelta;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyAfter;
        this.referenceId = referenceId;
        this.refType = refType;
        this.createdAt = LocalDateTime.now();
    }

    public static InventoryHistoryJpaEntity from(InventoryHistory inventoryHistory) {
        return new InventoryHistoryJpaEntity(
            inventoryHistory.getId(),
            inventoryHistory.getInventoryId(),
            inventoryHistory.getChangeType(),
            inventoryHistory.getDeltaQty(),
            inventoryHistory.getQtyBefore(),
            inventoryHistory.getQtyAfter(),
            inventoryHistory.getReferenceId(),
            inventoryHistory.getRefType()
        );
    }
}
