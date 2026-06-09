package com.fandrops.community.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class CommunityClockConfig {

    @Bean
    public Clock clock() {
        // KST 기준으로 LocalDate.now(clock)이 올바른 한국 날짜를 반환하도록 설정
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}