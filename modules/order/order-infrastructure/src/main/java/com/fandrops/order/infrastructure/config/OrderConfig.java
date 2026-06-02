package com.fandrops.order.infrastructure.config;

import com.fandrops.inventory.application.InventoryCommandService;
import com.fandrops.order.application.OrderService;
import com.fandrops.order.application.PaymentEventListener;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryConfirmPort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.InventoryRestorePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import com.fandrops.order.infrastructure.adapter.InventoryConfirmAdapter;
import com.fandrops.order.infrastructure.adapter.InventoryReserveAdapter;
import com.fandrops.order.infrastructure.adapter.InventoryRestoreAdapter;
import com.fandrops.order.infrastructure.adapter.StubProductPriceAdapter;
import com.fandrops.order.infrastructure.persistence.OrderJpaRepository;
import com.fandrops.order.infrastructure.persistence.OrderRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** order 모듈 빈 조립. 포트 구현체와 OrderService를 스프링 컨텍스트에 등록한다. */
@Configuration
@EnableAsync
public class OrderConfig {

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
                                     AccessTicketValidatePort accessTicketValidatePort,
                                     ProductPricePort productPricePort) {
        return new OrderService(orderRepository, inventoryReservePort, accessTicketValidatePort, productPricePort);
    }

    @Bean
    public PaymentEventListener paymentEventListener(OrderService orderService,
                                                     InventoryRestorePort inventoryRestorePort,
                                                     InventoryConfirmPort inventoryConfirmPort) {
        return new PaymentEventListener(orderService, inventoryRestorePort, inventoryConfirmPort);
    }
}
