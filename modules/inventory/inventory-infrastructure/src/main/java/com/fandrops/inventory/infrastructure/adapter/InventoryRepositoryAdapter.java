package com.fandrops.inventory.infrastructure.adapter;

import com.fandrops.inventory.domain.Inventory;
import com.fandrops.inventory.domain.port.InventoryReadRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaEntity;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class InventoryRepositoryAdapter implements InventoryReadRepository, InventoryRepository {

    private final InventoryJpaRepository jpaRepository;

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId)
            .map(InventoryJpaEntity::toDomain);
    }

    @Override
    public List<Inventory> findByProductIdIn(List<Long> productIds) {
        return jpaRepository.findByProductIdIn(productIds).stream()
                .map(InventoryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void save(Inventory inventory) {
        jpaRepository.save(InventoryJpaEntity.from(inventory));
    }

    @Override
    public int reserveAtomic(Long productId, int qty, Long orderId) {
        return jpaRepository.reserveAtomic(productId, qty);
    }

    @Override
    public int confirmAtomic(Long productId, int qty) {
        return jpaRepository.confirmAtomic(productId, qty);
    }

    @Override
    public int restoreAtomic(Long productId, int qty) {
        return jpaRepository.restoreAtomic(productId, qty);
    }
}
