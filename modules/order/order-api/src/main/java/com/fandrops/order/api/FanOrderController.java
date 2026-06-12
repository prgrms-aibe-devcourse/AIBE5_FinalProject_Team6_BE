package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.application.OrderService;
import com.fandrops.order.application.dto.OrderListResponse;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/fans/me/orders — 내 주문 목록 (cursor-based pagination). */
@RestController
@RequestMapping("/api/v1/fans")
public class FanOrderController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final Environment environment;

    public FanOrderController(OrderService orderService, Environment environment) {
        this.orderService = orderService;
        this.environment = environment;
    }

    @GetMapping("/me/orders")
    public ResponseEntity<ApiResponse<OrderListResponse>> getMyOrders(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        OrderListResponse response = orderService.getMyOrders(fanId, cursor, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    private Long resolveFanId(Authentication authentication, Long fanIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return fanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}
