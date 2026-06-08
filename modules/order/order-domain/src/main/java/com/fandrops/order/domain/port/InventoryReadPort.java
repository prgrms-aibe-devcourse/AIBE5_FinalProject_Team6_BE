package com.fandrops.order.domain.port;

import com.fandrops.order.domain.InventoryInfo;

/** 상품 상세 조회 시 재고 현황 읽기 포트. */
public interface InventoryReadPort {
    InventoryInfo getByProductId(Long productId);
}
