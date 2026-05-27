package com.fandrops.payment.infrastructure.config;

import com.fandrops.payment.application.queue.WaitQueueService;
import com.fandrops.payment.domain.queue.AccessTicketRepository;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WaitQueueConfig {

    @Bean
    public WaitQueueService waitQueueService(WaitQueueRepository waitQueueRepository,
                                             AccessTicketRepository accessTicketRepository) {
        return new WaitQueueService(waitQueueRepository, accessTicketRepository);
    }
}