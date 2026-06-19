package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryHistory;
import com.fandrops.inventory.domain.InventoryRefType;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaRepository;
import java.util.List;
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

    @Override
    public List<InventoryHistory> findByAgency(Long agencyId, Long artistId, Long productId, Long cursor, int size) {
        return jpaRepository.findByAgencyNative(agencyId, artistId, productId, cursor, size).stream()
                .map(e -> InventoryHistory.reconstitute(
                        e.getId(), e.getInventoryId(), e.getChangeType(),
                        e.getQtyDelta(), e.getQtyBefore(), e.getQtyAfter(),
                        e.getReferenceId(), e.getRefType(), e.getCreatedAt()))
                .toList();
    }
}
