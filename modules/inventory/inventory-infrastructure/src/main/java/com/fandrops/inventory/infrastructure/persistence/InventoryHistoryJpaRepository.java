package com.fandrops.inventory.infrastructure.persistence;

import com.fandrops.inventory.domain.InventoryChangeType;
import com.fandrops.inventory.domain.InventoryRefType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryHistoryJpaRepository extends JpaRepository<InventoryHistoryJpaEntity, Long> {

    boolean existsByReferenceIdAndRefTypeAndChangeType(Long referenceId, InventoryRefType refType, InventoryChangeType changeType);
}
