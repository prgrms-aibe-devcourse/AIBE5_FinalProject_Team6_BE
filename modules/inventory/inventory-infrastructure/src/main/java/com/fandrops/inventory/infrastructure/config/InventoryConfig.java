package com.fandrops.inventory.infrastructure.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.inventory.application.InventoryQueryService;
import com.fandrops.inventory.application.InventoryReserveTxHelper;
import com.fandrops.inventory.domain.port.InventoryHistoryRepository;
import com.fandrops.inventory.domain.port.InventoryReadRepository;
import com.fandrops.inventory.domain.port.InventoryRepository;
import com.fandrops.inventory.infrastructure.adapter.InventoryHistoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryOptimisticLockAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryReadRepositoryAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryRedissonLockAdapter;
import com.fandrops.inventory.infrastructure.adapter.InventoryRepositoryAdapter;
import com.fandrops.inventory.infrastructure.persistence.InventoryHistoryJpaRepository;
import com.fandrops.inventory.infrastructure.persistence.InventoryJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager entityManager;

    /** 재고 읽기 포트 — 전략과 무관하게 항상 읽기 전용 어댑터 사용. */
    @Bean
    public InventoryReadRepository inventoryReadRepository(InventoryJpaRepository jpaRepository) {
        return new InventoryReadRepositoryAdapter(jpaRepository);
    }

    /**
     * 재고 쓰기 포트 — 기본 전략: MySQL atomic UPDATE (WHERE available_qty >= qty).
     * fandrops.inventory.lock-strategy 미설정 시 활성화.
     */
    @Bean
    @ConditionalOnProperty(
            name = "fandrops.inventory.lock-strategy",
            havingValue = "atomic-update",
            matchIfMissing = true
    )
    public InventoryRepository inventoryAtomicRepository(InventoryJpaRepository jpaRepository) {
        return new InventoryRepositoryAdapter(jpaRepository);
    }

    /**
     * 재고 쓰기 포트 — 대안 전략: JPA @Version 낙관적 락 + Read-Check-Write.
     * fandrops.inventory.lock-strategy=optimistic 설정 시 활성화.
     */
    @Bean
    @ConditionalOnProperty(name = "fandrops.inventory.lock-strategy", havingValue = "optimistic")
    public InventoryRepository inventoryOptimisticRepository(InventoryJpaRepository jpaRepository) {
        return new InventoryOptimisticLockAdapter(jpaRepository, entityManager);
    }

    /**
     * 재고 쓰기 포트 — 대안 전략: Redisson 분산 락 + Read-Check-Write.
     * fandrops.inventory.lock-strategy=redisson 설정 시 활성화.
     */
    @Bean
    @ConditionalOnProperty(name = "fandrops.inventory.lock-strategy", havingValue = "redisson")
    public InventoryRepository inventoryRedissonRepository(
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
    public InventoryQueryService inventoryQueryService(InventoryHistoryRepository inventoryHistoryRepository) {
        return new InventoryQueryService(inventoryHistoryRepository);
    }

    @Bean
    public InventoryReserveTxHelper inventoryReserveTxHelper(
            InventoryReadRepository inventoryReadRepository,
            InventoryRepository inventoryRepository,
            InventoryHistoryRepository inventoryHistoryRepository) {
        return new InventoryReserveTxHelper(inventoryReadRepository, inventoryRepository, inventoryHistoryRepository);
    }

    @Bean
    public InventoryCommandService inventoryCommandService(
            InventoryReadRepository inventoryReadRepository,
            InventoryRepository inventoryRepository,
            InventoryHistoryRepository inventoryHistoryRepository,
            InventoryReserveTxHelper inventoryReserveTxHelper) {
        return new InventoryCommandService(inventoryReadRepository, inventoryRepository, inventoryHistoryRepository, inventoryReserveTxHelper);
    }
}
