package com.fandrops.config;

import com.fandrops.user.infrastructure.auth.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Profile("!local")
@Order(3)
@Configuration
public class ApiSecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public ApiSecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /** @Component 필터의 서블릿 자동 등록을 막아 Security 체인 내 1회만 실행되도록 한다. */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)  // REST API는 쿠키 대신 Bearer 토큰 사용하기 떄문에 CSRF 공격 불가능 따라서 비활성화
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))   // 토큰 자체에 정보가 있어서 서버가 기억할 필요가 없음. -> 세션 안 만듦
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)   // 필터 순서에 맞게 끼워넣기
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        // 공개 피드 조회 — 비인증 브라우징 허용 (타 모듈 공개 경로 추가 시 여기에 등록)
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/artists/*/feeds").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/banners/main").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/b2b/apply").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                )
                .build();
    }
}
