package com.fandrops.inventory.application.dto;

import com.fandrops.inventory.domain.InventoryHistory;
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
        LocalDateTime changedAt
) {
    public static InventoryHistoryItem from(InventoryHistory h) {
        return new InventoryHistoryItem(
                h.getId(),
                h.getInventoryId(),
                h.getChangeType().name(),
                h.getDeltaQty(),
                h.getQtyBefore(),
                h.getQtyAfter(),
                h.getReferenceId(),
                h.getRefType().name(),
                h.getChangedAt()
        );
    }
}
