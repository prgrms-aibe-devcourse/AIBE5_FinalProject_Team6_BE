package com.fandrops.inventory.domain.port;

import com.fandrops.inventory.domain.Inventory;
import java.util.List;
import java.util.Optional;

/** 재고 읽기 전용 포트. 락 없는 단순 JPA 조회만 수행한다. */
public interface InventoryReadRepository {
    Optional<Inventory> findByProductId(Long productId);
    List<Inventory> findByProductIdIn(List<Long> productIds);
}
