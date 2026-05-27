package com.fandrops.ops;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Actuator 엔드포인트 보안 설정
 *
 * <p>/actuator/health, /actuator/prometheus 는 인증 없이 접근 허용:
 * <ul>
 *   <li>health  — CD 파이프라인 헬스체크</li>
 *   <li>prometheus — Prometheus 스크레이핑</li>
 * </ul>
 * 그 외 actuator 경로는 모두 거부한다.
 */
@Configuration
public class OpsSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .anyRequest().denyAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
