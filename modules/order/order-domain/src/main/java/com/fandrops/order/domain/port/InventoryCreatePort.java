package com.fandrops.order.domain.port;

/** 상품 등록 시 초기 재고 행 생성 포트. */
public interface InventoryCreatePort {
    void createInventory(Long productId, int totalQty);
}
