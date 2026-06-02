package com.fandrops.order.domain.port;

/** 재고 복구 포트. Saga 보상 트랜잭션에서 RESERVED 선점을 해제한다. */
public interface InventoryRestorePort {

    void restore(Long productId, int quantity, Long orderId);
}
