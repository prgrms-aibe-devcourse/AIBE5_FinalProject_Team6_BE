package com.fandrops.user.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
public class AsyncConfig {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-");
        // 큐(100) + maxPool(5) 포화 시 기본 AbortPolicy는 리스너 try/catch 진입 전에 예외를 던져
        // Prometheus 카운터가 증가하지 않는다. 로그로라도 가시성 확보.
        executor.setRejectedExecutionHandler((task, exec) ->
                log.error("[emailExecutor] 이메일 발송 작업 거부됨 — 큐 포화. task={}", task));
        // 배포 시 SMTP 전송 중인 작업이 강제 종료되지 않도록 대기
        // connectiontimeout(5s) + read(10s) + write(10s) = 최악 25s → 30s로 여유 확보
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
