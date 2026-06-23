package com.fandrops.payment.infrastructure.config;

import com.fandrops.payment.application.queue.WaitQueueService;
import com.fandrops.payment.domain.queue.AccessTicketRepository;
import com.fandrops.payment.domain.queue.WaitQueueRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class WaitQueueConfig {

    @Value("${fandrops.queue.processing-timeout-seconds:600}")
    private long processingTimeoutSeconds;

    @Value("${fandrops.queue.max-concurrent-processing:10}")
    private long maxConcurrentProcessing;

    @Bean
    public WaitQueueService waitQueueService(WaitQueueRepository waitQueueRepository,
                                             AccessTicketRepository accessTicketRepository) {
        return new WaitQueueService(waitQueueRepository, accessTicketRepository,
                processingTimeoutSeconds, maxConcurrentProcessing);
    }
}