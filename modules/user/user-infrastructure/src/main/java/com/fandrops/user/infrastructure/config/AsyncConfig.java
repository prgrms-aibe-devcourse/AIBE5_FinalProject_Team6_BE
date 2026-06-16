package com.fandrops.user.infrastructure.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        // 큐(100) + maxPool(5) 포화 시 리스너 try/catch 진입 전에 거부됨 — 카운터 직접 증가
        executor.setRejectedExecutionHandler((task, exec) -> {
            log.error("[emailExecutor] 이메일 발송 작업 거부됨 — 큐 포화. task={}", task);
            meterRegistry.counter("fandrops_email_send_errors_total", "type", "queue_rejected").increment();
        });
        // 배포 시 SMTP 전송 중인 작업이 강제 종료되지 않도록 대기
        // connectiontimeout(5s) + read(10s) + write(10s) = 최악 25s → 30s로 여유 확보
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
