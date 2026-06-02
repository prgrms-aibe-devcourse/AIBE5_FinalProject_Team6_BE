package com.fandrops.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fandrops.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService,
                           RateLimitProperties props,
                           ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String group = resolveGroup(request.getRequestURI(), request.getMethod());
        if (group == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long fanId = resolveFanId();
        if (fanId == null) {
            // 미인증 요청 — Nginx IP 기반 limit_req가 1차 방어
            filterChain.doFilter(request, response);
            return;
        }

        int limit = resolveLimit(group);
        if (!rateLimitService.isAllowed(fanId, group, limit, props.getWindowMs())) {
            rejectWith429(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveGroup(String uri, String method) {
        if (!"POST".equals(method)) return null;
        if (uri.startsWith("/api/v1/queue/join/")) return "queue";
        if ("/api/v1/orders".equals(uri)) return "order";
        if ("/api/v1/payments/toss/confirm".equals(uri)) return "payment";
        return null;
    }

    private int resolveLimit(String group) {
        return switch (group) {
            case "queue" -> props.getQueueJoinPerMinute();
            case "order" -> props.getOrderPerMinute();
            case "payment" -> props.getPaymentPerMinute();
            default -> Integer.MAX_VALUE;
        };
    }

    private Long resolveFanId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void rejectWith429(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");
        ApiResponse<?> body = ApiResponse.fail(
                "RATE_LIMITED",
                "요청이 너무 많습니다. 잠시 후 다시 시도해주세요.",
                true,
                MDC.get("traceId")
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}