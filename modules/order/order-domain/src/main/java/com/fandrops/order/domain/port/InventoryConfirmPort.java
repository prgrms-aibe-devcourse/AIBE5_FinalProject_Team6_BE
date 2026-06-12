package com.fandrops.order.domain.port;

/** 재고 확정 포트. 결제 승인 후 RESERVED 수량을 영구 차감한다. */
public interface InventoryConfirmPort {

    void confirm(Long productId, int quantity, Long orderId);
}
