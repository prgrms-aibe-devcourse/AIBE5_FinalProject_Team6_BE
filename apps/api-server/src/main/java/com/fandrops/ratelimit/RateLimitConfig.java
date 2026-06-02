package com.fandrops.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@Profile("!local")
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimitService rateLimitService(StringRedisTemplate redisTemplate) {
        return new RateLimitService(redisTemplate);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimitService rateLimitService,
            RateLimitProperties props,
            ObjectMapper objectMapper) {
        RateLimitFilter filter = new RateLimitFilter(rateLimitService, props, objectMapper);
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(
                "/api/v1/orders",
                "/api/v1/queue/join/*",
                "/api/v1/payments/toss/confirm"
        );
        registration.setOrder(0); // Spring Security(-100) 이후
        return registration;
    }
}