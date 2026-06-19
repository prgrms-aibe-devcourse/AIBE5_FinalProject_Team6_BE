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

/** GET /api/v1/agency/orders — agency 소속 아티스트 주문 목록 (cursor-based pagination). */
@RestController
@RequestMapping("/api/v1/agency")
public class AgencyOrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final Environment environment;

    public AgencyOrderController(OrderService orderService, Environment environment) {
        this.orderService = orderService;
        this.environment = environment;
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<OrderListResponse>> getAgencyOrders(
            Authentication authentication,
            @RequestHeader(value = "X-Agency-Id", required = false) Long agencyIdHeader,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long agencyId = resolveAgencyId(authentication, agencyIdHeader);
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        OrderListResponse response = orderService.getAgencyOrders(agencyId, artistId, cursor, pageSize);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    private Long resolveAgencyId(Authentication authentication, Long agencyIdHeader) {
        if (agencyIdHeader != null && isLocalProfile()) {
            return agencyIdHeader;
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
