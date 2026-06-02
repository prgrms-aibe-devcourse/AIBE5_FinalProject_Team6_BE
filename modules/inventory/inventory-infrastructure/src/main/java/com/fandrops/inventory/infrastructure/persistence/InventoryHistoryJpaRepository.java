package com.fandrops.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryHistoryJpaRepository extends JpaRepository<InventoryHistoryJpaEntity, Long> {
}
