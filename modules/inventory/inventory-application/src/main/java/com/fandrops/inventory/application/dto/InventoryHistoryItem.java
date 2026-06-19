package com.fandrops.inventory.application.dto;

import com.fandrops.inventory.domain.InventoryHistorySummary;
import java.time.LocalDateTime;

public record InventoryHistoryItem(
        Long historyId,
        Long inventoryId,
        String changeType,
        int deltaQty,
        int qtyBefore,
        int qtyAfter,
        Long referenceId,
        String refType,
        LocalDateTime changedAt,
        Long productId,
        String productName
) {
    public static InventoryHistoryItem from(InventoryHistorySummary s) {
        return new InventoryHistoryItem(
                s.id(), s.inventoryId(), s.changeType().name(),
                s.deltaQty(), s.qtyBefore(), s.qtyAfter(),
                s.referenceId(), s.refType().name(), s.changedAt(),
                s.productId(), s.productName()
        );
    }
}
