package com.fandrops.order.domain.port;

import com.fandrops.order.domain.exception.OutOfStockException;
import com.fandrops.order.domain.exception.ReserveConflictException;

/** 재고 예약 포트. feat/24 머지 후 inventory-application 실제 구현체로 교체 예정. */
public interface InventoryReservePort {

    /**
     * 재고 예약. 재고 0이면 OutOfStockException, 수량 부족이면 ReserveConflictException.
     * feat/24 머지 후 실제 구현체로 교체 예정.
     */
    void reserve(Long productId, int quantity, Long orderId);
}
