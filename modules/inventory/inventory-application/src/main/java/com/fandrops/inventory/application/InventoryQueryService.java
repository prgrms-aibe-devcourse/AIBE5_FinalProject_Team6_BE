package com.fandrops.inventory.application;

import com.fandrops.inventory.application.dto.InventoryHistoryItem;
import com.fandrops.inventory.application.dto.InventoryHistoryListResponse;
import com.fandrops.inventory.domain.InventoryHistory;
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
        List<InventoryHistory> histories =
                inventoryHistoryRepository.findByAgency(agencyId, artistId, productId, cursor, size);
        List<InventoryHistoryItem> items = histories.stream()
                .map(InventoryHistoryItem::from)
                .toList();
        Long nextCursor = histories.size() == size ? histories.get(histories.size() - 1).getId() : null;
        return new InventoryHistoryListResponse(items, nextCursor);
    }
}
