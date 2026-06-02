package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class InventoryRepositoryAdapter implements InventoryRepository {

    private final InventoryJpaRepository jpaRepository;

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId)
            .map(InventoryJpaEntity::toDomain);
    }

    @Override
    public void save(Inventory inventory) {
        jpaRepository.save(InventoryJpaEntity.from(inventory));
    }
}
