package com.fandrops.inventory.infrastructure.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.adapter.InventoryHistoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryRedissonLockAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InventoryConfig {

    /**
     * 기본 전략: MySQL atomic UPDATE (WHERE available_qty >= qty).
     * fandrops.inventory.lock-strategy 미설정 시 활성화.
     */
    @Bean
    @ConditionalOnProperty(
            name = "fandrops.inventory.lock-strategy",
            havingValue = "atomic-update",
            matchIfMissing = true
    )
    public InventoryRepositoryAdapter inventoryRepositoryAdapter(InventoryJpaRepository jpaRepository) {
        return new InventoryRepositoryAdapter(jpaRepository);
    }

    /**
     * 대안 전략: Redisson 분산 락 + Read-Check-Write.
     * fandrops.inventory.lock-strategy=redisson 설정 시 활성화.
     */
    @Bean
    @ConditionalOnProperty(name = "fandrops.inventory.lock-strategy", havingValue = "redisson")
    public InventoryRedissonLockAdapter inventoryRedissonLockAdapter(
            InventoryJpaRepository jpaRepository,
            RedissonClient redissonClient) {
        return new InventoryRedissonLockAdapter(jpaRepository, redissonClient);
    }

    @Bean
    @ConditionalOnProperty(name = "fandrops.inventory.lock-strategy", havingValue = "redisson")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled) {
        Config config = new Config();
        String scheme = sslEnabled ? "rediss://" : "redis://";
        // ElastiCache 연결 한도 초과 방지: 기본값(pool=64, idle=24)을 EC2 인스턴스 수를 감안해 명시적으로 제한
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(scheme + redisHost + ":" + redisPort)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2);
        if (!redisPassword.isEmpty()) {
            serverConfig.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }

    @Bean
    public InventoryHistoryRepositoryAdapter inventoryHistoryRepositoryAdapter(
            InventoryHistoryJpaRepository jpaRepository) {
        return new InventoryHistoryRepositoryAdapter(jpaRepository);
    }

    @Bean
    public InventoryCommandService inventoryCommandService(
            InventoryRepository inventoryRepository,
            InventoryHistoryRepository inventoryHistoryRepository) {
        return new InventoryCommandService(inventoryRepository, inventoryHistoryRepository);
    }
}
