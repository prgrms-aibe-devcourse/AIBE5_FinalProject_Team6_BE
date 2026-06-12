package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.api.dto.CreateProductRequest;
import com.fandrops.order.api.dto.RestockRequest;
import com.fandrops.order.api.dto.UpdateProductRequest;
import com.fandrops.order.application.ProductService;
import com.fandrops.order.application.RestockAlertService;
import com.fandrops.order.application.dto.CreateProductCommand;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.dto.ProductResponse;
import com.fandrops.order.application.dto.UpdateProductCommand;
import com.fandrops.order.domain.ProductStatus;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;
    private final RestockAlertService restockAlertService;
    private final Environment environment;

    public ProductController(ProductService productService,
                             RestockAlertService restockAlertService,
                             Environment environment) {
        this.productService = productService;
        this.restockAlertService = restockAlertService;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponse>> getProducts(
            @RequestParam(defaultValue = "regular") String type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProducts(type, cursor, size), traceId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                productService.getProduct(id), traceId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        Long productId = productService.createProduct(new CreateProductCommand(
                request.getArtistId(), request.getName(), request.getPrice(), request.getTotalQty(),
                request.getDropsStartAt(), request.getDropsEndAt()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("productId", productId), traceId()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProduct(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductStatus status = productService.updateProduct(new UpdateProductCommand(
                id, request.getName(), request.getPrice(), request.getStatus(),
                request.getDropsStartAt(), request.getDropsEndAt()));
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("productId", id, "status", status.name()), traceId()));
    }

    @PostMapping("/{id}/restock-subscribe")
    public ResponseEntity<ApiResponse<Map<String, Long>>> subscribe(
            @PathVariable("id") Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        Long alertId = restockAlertService.subscribe(fanId, productId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("alertId", alertId), traceId()));
    }

    @DeleteMapping("/{id}/restock-subscribe")
    public ResponseEntity<Void> unsubscribe(
            @PathVariable("id") Long productId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        restockAlertService.unsubscribe(fanId, productId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> restock(
            @PathVariable("id") Long productId,
            @Valid @RequestBody RestockRequest request) {
        int totalQty = restockAlertService.restock(productId, request.getQuantity());
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("productId", productId, "totalQty", totalQty), traceId()));
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

    private String traceId() {
        return MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
    }
}
