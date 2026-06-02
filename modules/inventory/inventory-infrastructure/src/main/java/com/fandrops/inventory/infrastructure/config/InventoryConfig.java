package com.fandrops.inventory.infrastructure.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.infrastructure.adapter.InventoryHistoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryConfig {

    @Bean
    public InventoryRepositoryAdapter inventoryRepositoryAdapter(InventoryJpaRepository jpaRepository) {
        return new InventoryRepositoryAdapter(jpaRepository);
    }

    @Bean
    public InventoryHistoryRepositoryAdapter inventoryHistoryRepositoryAdapter(InventoryHistoryJpaRepository jpaRepository) {
        return new InventoryHistoryRepositoryAdapter(jpaRepository);
    }

    @Bean
    public InventoryCommandService inventoryCommandService(InventoryRepositoryAdapter inventoryRepositoryAdapter, InventoryHistoryRepositoryAdapter inventoryHistoryRepositoryAdapter) {
        return new InventoryCommandService(inventoryRepositoryAdapter, inventoryHistoryRepositoryAdapter);
    }
}
