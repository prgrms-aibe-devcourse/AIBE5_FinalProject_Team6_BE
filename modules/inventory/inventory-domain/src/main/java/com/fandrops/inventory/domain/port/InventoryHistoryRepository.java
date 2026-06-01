package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.InventoryHistory;

public interface InventoryHistoryRepository {

    void save(InventoryHistory history);
}
