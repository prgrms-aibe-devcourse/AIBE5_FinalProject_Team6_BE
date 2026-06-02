package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.Inventory;

import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findByProductId(Long productId);

    void save(Inventory inventory);

    /**
     * 단일 UPDATE WHERE available_qty >= qty 로 재고를 원자적으로 예약한다.
     * @return 업데이트된 row 수 (1=성공, 0=재고 부족)
     */
    int reserveAtomic(Long productId, int qty);
}
