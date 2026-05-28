package com.fandrops.order.infrastructure.config;

import com.fandrops.order.application.OrderService;
import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.InventoryReservePort;
import com.fandrops.order.domain.port.OrderRepository;
import com.fandrops.order.domain.port.ProductPricePort;
import com.fandrops.payment.domain.queue.AccessTicketRepository;
import com.fandrops.order.infrastructure.adapter.AccessTicketValidateAdapter;
import com.fandrops.order.infrastructure.adapter.StubInventoryReserveAdapter;
import com.fandrops.order.infrastructure.adapter.StubProductPriceAdapter;
import com.fandrops.order.infrastructure.persistence.OrderJpaRepository;
import com.fandrops.order.infrastructure.persistence.OrderRepositoryAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** order 모듈 빈 조립. 포트 구현체와 OrderService를 스프링 컨텍스트에 등록한다. */
@Configuration
public class OrderConfig {

    @Bean
    public OrderRepository orderRepository(OrderJpaRepository jpaRepository) {
        return new OrderRepositoryAdapter(jpaRepository);
    }

    @Bean
    public AccessTicketValidatePort accessTicketValidatePort(AccessTicketRepository accessTicketRepository) {
        return new AccessTicketValidateAdapter(accessTicketRepository);
    }

    @Bean
    public InventoryReservePort inventoryReservePort() {
        return new StubInventoryReserveAdapter();
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
}
