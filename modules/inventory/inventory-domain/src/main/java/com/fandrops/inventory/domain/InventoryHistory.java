package com.fandrops.inventory.domain;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class InventoryHistory {

    private final Long id;
    private final Long inventoryId;
    private final InventoryChangeType changeType;
    private final int deltaQty;
    private final int qtyBefore;
    private final int qtyAfter;
    private final Long referenceId;
    private final InventoryRefType refType;
    private final LocalDateTime changedAt;

    private InventoryHistory(Long id, Long inventoryId, InventoryChangeType changeType,
                             int deltaQty, int qtyBefore, int qtyAfter,
                             Long referenceId, InventoryRefType refType, LocalDateTime changedAt) {
        this.id = id;
        this.inventoryId = inventoryId;
        this.changeType = changeType;
        this.deltaQty = deltaQty;
        this.qtyBefore = qtyBefore;
        this.qtyAfter = qtyAfter;
        this.referenceId = referenceId;
        this.refType = refType;
        this.changedAt = changedAt;
    }

    public static InventoryHistory of(Long inventoryId, InventoryChangeType changeType,
                                      int deltaQty, int qtyBefore, int qtyAfter,
                                      Long referenceId, InventoryRefType refType, LocalDateTime changedAt) {
        return new InventoryHistory(null, inventoryId, changeType,
                deltaQty, qtyBefore, qtyAfter, referenceId, refType, changedAt);
    }

    public static InventoryHistory of(Long inventoryId, InventoryChangeType changeType,
                                      int deltaQty, int qtyBefore, int qtyAfter,
                                      Long referenceId, InventoryRefType refType) {
        return of(inventoryId, changeType, deltaQty, qtyBefore, qtyAfter,
                referenceId, refType, LocalDateTime.now());
    }
}
