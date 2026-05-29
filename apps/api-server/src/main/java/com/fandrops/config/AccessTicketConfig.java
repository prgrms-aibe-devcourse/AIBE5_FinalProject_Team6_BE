package com.fandrops.config;

import com.fandrops.order.domain.port.AccessTicketValidatePort;
import com.fandrops.order.domain.port.AccessTicketValidator;
import com.fandrops.order.infrastructure.adapter.AccessTicketValidateAdapter;
import com.fandrops.payment.domain.queue.AccessTicketRepository;
import com.fandrops.payment.infrastructure.queue.PaymentAccessTicketValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** order-domain ↔ payment-infrastructure 교차 모듈 DI 연결. */
@Configuration
public class AccessTicketConfig {

    @Bean
    public AccessTicketValidator accessTicketValidator(AccessTicketRepository accessTicketRepository) {
        return new PaymentAccessTicketValidator(accessTicketRepository);
    }

    @Bean
    public AccessTicketValidatePort accessTicketValidatePort(AccessTicketValidator accessTicketValidator) {
        return new AccessTicketValidateAdapter(accessTicketValidator);
    }
}
