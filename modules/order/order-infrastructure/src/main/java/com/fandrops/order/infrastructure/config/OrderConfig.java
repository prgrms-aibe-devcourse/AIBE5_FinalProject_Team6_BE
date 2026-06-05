package com.fandrops.order.infrastructure.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.order.application.OrderService;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import com.fandrops.order.application.CartService;
import com.fandrops.order.domain.port.CartItemRepository;
import com.fandrops.order.domain.port.CartRepository;
import com.fandrops.order.infrastructure.adapter.CartItemRepositoryAdapter;
import com.fandrops.order.infrastructure.adapter.CartRepositoryAdapter;
import com.fandrops.order.infrastructure.adapter.InventoryConfirmAdapter;
import com.fandrops.order.infrastructure.adapter.InventoryReserveAdapter;
import com.fandrops.order.infrastructure.adapter.InventoryRestoreAdapter;
import com.fandrops.order.infrastructure.adapter.StubProductPriceAdapter;
import com.fandrops.order.infrastructure.persistence.CartItemJpaRepository;
import com.fandrops.order.infrastructure.persistence.CartJpaRepository;
import com.fandrops.order.infrastructure.persistence.OrderJpaRepository;
import com.fandrops.order.infrastructure.persistence.OrderRepositoryAdapter;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** order 모듈 빈 조립. 포트 구현체와 OrderService를 스프링 컨텍스트에 등록한다. */
@Configuration
@EnableAsync
public class OrderConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(OrderConfig.class);

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("[Saga 비동기 예외] {}.{} — params: {}",
                        method.getDeclaringClass().getSimpleName(), method.getName(), params, ex);
    }

    @Bean
    public OrderRepository orderRepository(OrderJpaRepository jpaRepository) {
        return new OrderRepositoryAdapter(jpaRepository);
    }

    // AccessTicketValidatePort 빈은 apps/api-server에서 PaymentAccessTicketValidator(payment-infrastructure)로 등록.

    @Bean
    public InventoryReservePort inventoryReservePort(InventoryCommandService inventoryCommandService) {
        return new InventoryReserveAdapter(inventoryCommandService);
    }

    @Bean
    public InventoryRestorePort inventoryRestorePort(InventoryCommandService inventoryCommandService) {
        return new InventoryRestoreAdapter(inventoryCommandService);
    }

    @Bean
    public InventoryConfirmPort inventoryConfirmPort(InventoryCommandService inventoryCommandService) {
        return new InventoryConfirmAdapter(inventoryCommandService);
    }

    @Bean
    public ProductPricePort productPricePort() {
        return new StubProductPriceAdapter();
    }

    @Bean
    public OrderService orderService(OrderRepository orderRepository,
                                     InventoryReservePort inventoryReservePort,
                                     InventoryRestorePort inventoryRestorePort,
                                     AccessTicketValidatePort accessTicketValidatePort,
                                     ProductPricePort productPricePort) {
        return new OrderService(orderRepository, inventoryReservePort, inventoryRestorePort,
                accessTicketValidatePort, productPricePort);
    }

    @Bean
    public CartRepository cartRepository(CartJpaRepository jpaRepository) {
        return new CartRepositoryAdapter(jpaRepository);
    }

    @Bean
    public CartItemRepository cartItemRepository(CartItemJpaRepository jpaRepository) {
        return new CartItemRepositoryAdapter(jpaRepository);
    }

    @Bean
    public CartService cartService(CartRepository cartRepository,
                                   CartItemRepository cartItemRepository,
                                   ProductPricePort productPricePort) {
        return new CartService(cartRepository, cartItemRepository, productPricePort);
    }

    @Bean(name = "sagaExecutor")
    public Executor sagaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("saga-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

}
