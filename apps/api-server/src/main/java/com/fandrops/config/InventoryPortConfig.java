package com.fandrops.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.port.InventoryCreatePort;
import com.fandrops.order.domain.port.InventoryIncreasePort;
import com.fandrops.order.domain.port.InventoryReadPort;
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
        return (productId, qty) -> inventoryCommandService.increase(productId, productId, qty);
    }

    @Bean
    public InventoryReadPort inventoryReadPort(InventoryCommandService inventoryCommandService) {
        return productId -> {
            var inv = inventoryCommandService.getInventoryByProductId(productId);
            return new InventoryInfo(inv.getTotalQty(), inv.getReservedQty(), inv.getAvailableQty());
        };
    }
}
