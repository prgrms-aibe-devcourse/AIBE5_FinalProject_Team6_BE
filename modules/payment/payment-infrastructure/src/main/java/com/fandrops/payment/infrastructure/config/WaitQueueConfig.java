package com.fandrops.payment.infrastructure.config;

import com.fandrops.payment.application.queue.WaitQueueService;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WaitQueueConfig {

    @Bean
    public WaitQueueService waitQueueService(WaitQueueRepository waitQueueRepository) {
        return new WaitQueueService(waitQueueRepository);
    }
}