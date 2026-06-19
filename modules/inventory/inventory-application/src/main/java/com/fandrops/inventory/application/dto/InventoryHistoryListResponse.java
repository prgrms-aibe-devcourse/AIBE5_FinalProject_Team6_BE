package com.fandrops.inventory.application.dto;

import java.util.List;

public record InventoryHistoryListResponse(
        List<InventoryHistoryItem> items,
        Long nextCursor
) {
}
