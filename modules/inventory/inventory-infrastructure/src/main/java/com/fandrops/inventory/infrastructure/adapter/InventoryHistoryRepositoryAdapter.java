package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaRepository;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InventoryHistoryRepositoryAdapter implements InventoryHistoryRepository {

    private final InventoryHistoryJpaRepository jpaRepository;

    @Override
    public void save(InventoryHistory history) {
        jpaRepository.save(InventoryHistoryJpaEntity.from(history));
    }

    @Override
    public boolean existsByReferenceIdAndRefTypeAndChangeType(Long referenceId, InventoryRefType refType, InventoryChangeType changeType) {
        return jpaRepository.existsByReferenceIdAndRefTypeAndChangeType(referenceId, refType, changeType);
    }
}
