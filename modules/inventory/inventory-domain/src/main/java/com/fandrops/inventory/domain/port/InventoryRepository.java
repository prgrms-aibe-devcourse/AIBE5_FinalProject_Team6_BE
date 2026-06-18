package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.Inventory;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository {

    Optional<Inventory> findByProductId(Long productId);

    List<Inventory> findByProductIdIn(List<Long> productIds);

    void save(Inventory inventory);

    /**
     * 단일 UPDATE WHERE available_qty >= qty 로 재고를 원자적으로 예약한다.
     * @return 업데이트된 row 수 (1=성공, 0=재고 부족)
     */
    int reserveAtomic(Long productId, int qty);

    /**
     * 단일 UPDATE WHERE reserved_qty >= qty 로 재고를 원자적으로 확정한다.
     * total_qty, reserved_qty 감소 (available_qty 불변).
     * @return 업데이트된 row 수 (1=성공, 0=상태 불일치)
     */
    int confirmAtomic(Long productId, int qty);

    /**
     * 단일 UPDATE WHERE reserved_qty >= qty 로 재고를 원자적으로 복구한다.
     * reserved_qty 감소, available_qty 증가.
     * @return 업데이트된 row 수 (1=성공, 0=상태 불일치)
     */
    int restoreAtomic(Long productId, int qty);
}
