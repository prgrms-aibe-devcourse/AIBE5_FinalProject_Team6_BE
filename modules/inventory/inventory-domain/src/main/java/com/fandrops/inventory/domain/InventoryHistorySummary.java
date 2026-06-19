package com.fandrops.inventory.domain;

import java.time.LocalDateTime;

public record InventoryHistorySummary(
        Long id,
        Long inventoryId,
        InventoryChangeType changeType,
        int deltaQty,
        int qtyBefore,
        int qtyAfter,
        Long referenceId,
        InventoryRefType refType,
        LocalDateTime changedAt,
        Long productId,
        String productName
) {}
