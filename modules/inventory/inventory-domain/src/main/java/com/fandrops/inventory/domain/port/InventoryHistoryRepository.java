package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;

public interface InventoryHistoryRepository {

    void save(InventoryHistory history);

    boolean existsByReferenceIdAndRefTypeAndChangeType(Long referenceId, InventoryRefType refType, InventoryChangeType changeType);
}
