package com.fandrops.inventory.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryJpaRepository extends JpaRepository<InventoryJpaEntity, Long> {
    Optional<InventoryJpaEntity> findByProductId(Long productId);
}
