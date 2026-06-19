package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;

import java.util.List;

public interface InventoryHistoryRepository {

    void save(InventoryHistory history);

    boolean existsByReferenceIdAndRefTypeAndChangeType(Long referenceId, InventoryRefType refType, InventoryChangeType changeType);

    /** agency 소속 아티스트 재고 이력. artistId·productId null이면 전체. cursor 미제공 시 첫 페이지. */
    List<InventoryHistory> findByAgency(Long agencyId, Long artistId, Long productId, Long cursor, int size);
}
