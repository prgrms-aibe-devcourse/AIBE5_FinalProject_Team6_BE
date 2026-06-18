package com.fandrops.order.domain.port;

import com.fandrops.order.domain.InventoryInfo;
import java.util.List;
import java.util.Map;

/** 재고 현황 읽기 포트. */
public interface InventoryReadPort {
    InventoryInfo getByProductId(Long productId);
    Map<Long, InventoryInfo> getByProductIds(List<Long> productIds);
}
