package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.Inventory;

import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findByProductId(Long productId);

    Inventory save(Inventory inventory);
}
