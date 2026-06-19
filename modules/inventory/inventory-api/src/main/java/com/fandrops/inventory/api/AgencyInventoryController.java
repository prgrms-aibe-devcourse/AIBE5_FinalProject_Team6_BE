package com.fandrops.inventory.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.inventory.application.InventoryQueryService;
import com.fandrops.inventory.application.dto.InventoryHistoryListResponse;
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

/** GET /api/v1/agency/inventory/history — agency 소속 아티스트 재고 이력 (cursor-based pagination). */
@RestController
@RequestMapping("/api/v1/agency/inventory")
public class AgencyInventoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryQueryService inventoryQueryService;
    private final Environment environment;

    public AgencyInventoryController(InventoryQueryService inventoryQueryService, Environment environment) {
        this.inventoryQueryService = inventoryQueryService;
        this.environment = environment;
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<InventoryHistoryListResponse>> getInventoryHistory(
            Authentication authentication,
            @RequestHeader(value = "X-Agency-Id", required = false) Long agencyIdHeader,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        Long agencyId = resolveAgencyId(authentication, agencyIdHeader);
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        InventoryHistoryListResponse response =
                inventoryQueryService.getAgencyInventoryHistory(agencyId, artistId, productId, cursor, pageSize);
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
