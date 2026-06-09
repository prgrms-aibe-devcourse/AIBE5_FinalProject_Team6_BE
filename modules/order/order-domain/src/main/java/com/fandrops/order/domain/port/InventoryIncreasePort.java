package com.fandrops.order.domain.port;

/** 재입고 처리 시 재고 증가 포트. */
public interface InventoryIncreasePort {
    void increase(Long productId, int qty);
}
