package com.fandrops.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.port.InventoryCreatePort;
import com.fandrops.order.domain.port.InventoryIncreasePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** order-domain ↔ inventory-application 교차 모듈 DI 연결. order-infrastructure는 inventory를 모른다. */
@Configuration
public class InventoryPortConfig {

    @Bean
    public InventoryCreatePort inventoryCreatePort(InventoryCommandService inventoryCommandService) {
        return (productId, totalQty) -> inventoryCommandService.createInventory(productId, totalQty);
    }

    @Bean
    public InventoryIncreasePort inventoryIncreasePort(InventoryCommandService inventoryCommandService) {
        // restockId 자리에 productId를 임시 사용. MVP에 별도 restock 엔티티 없음.
        // F04-05 이후 Restock 도메인 추가 시 restockId로 교체 필요.
        return (productId, qty) -> inventoryCommandService.increase(productId, productId, qty);
    }

    @Bean
    public InventoryReadPort inventoryReadPort(InventoryCommandService inventoryCommandService) {
        return new InventoryReadPort() {
            @Override
            public InventoryInfo getByProductId(Long productId) {
                var inv = inventoryCommandService.getInventoryByProductId(productId);
                return new InventoryInfo(inv.getTotalQty(), inv.getReservedQty(), inv.getAvailableQty());
            }

            @Override
            public Map<Long, InventoryInfo> getByProductIds(List<Long> productIds) {
                return inventoryCommandService.getInventoryByProductIds(productIds).entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> new InventoryInfo(e.getValue().getTotalQty(),
                                        e.getValue().getReservedQty(),
                                        e.getValue().getAvailableQty())));
            }
        };
    }
}
