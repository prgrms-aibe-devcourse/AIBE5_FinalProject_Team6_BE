package com.fandrops.inventory.application;

import com.fandrops.inventory.application.dto.InventoryHistoryItem;
import com.fandrops.inventory.application.dto.InventoryHistoryListResponse;
import com.fandrops.inventory.domain.InventoryHistorySummary;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class InventoryQueryService {

    private final InventoryHistoryRepository inventoryHistoryRepository;

    public InventoryQueryService(InventoryHistoryRepository inventoryHistoryRepository) {
        this.inventoryHistoryRepository = inventoryHistoryRepository;
    }

    @Transactional(readOnly = true)
    public InventoryHistoryListResponse getAgencyInventoryHistory(
            Long agencyId, Long artistId, Long productId, Long cursor, int size) {
        List<InventoryHistorySummary> summaries =
                inventoryHistoryRepository.findAgencySummaries(agencyId, artistId, productId, cursor, size);
        List<InventoryHistoryItem> items = summaries.stream()
                .map(InventoryHistoryItem::from)
                .toList();
        Long nextCursor = summaries.size() == size ? summaries.get(summaries.size() - 1).id() : null;
        return new InventoryHistoryListResponse(items, nextCursor);
    }
}
