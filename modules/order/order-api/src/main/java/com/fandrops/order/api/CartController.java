package com.fandrops.order.api;

import com.fandrops.order.api.dto.AddCartItemRequest;
import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.api.dto.UpdateCartItemRequest;
import com.fandrops.order.application.CartService;
import com.fandrops.order.application.dto.AddCartItemCommand;
import com.fandrops.order.application.dto.CartResponse;
import com.fandrops.order.application.dto.UpdateCartItemCommand;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;
    private final Environment environment;

    public CartController(CartService cartService, Environment environment) {
        this.cartService = cartService;
        this.environment = environment;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        return ResponseEntity.ok(ApiResponse.ok(cartService.getCart(fanId), traceId()));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Map<String, Long>>> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        Long cartItemId = cartService.addItem(
                new AddCartItemCommand(fanId, request.getProductId(), request.getQuantity()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(Map.of("cartItemId", cartItemId), traceId()));
    }

    @PatchMapping("/items/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateItem(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        cartService.updateItemQuantity(new UpdateCartItemCommand(fanId, id, request.getQuantity()));
        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("cartItemId", id, "quantity", request.getQuantity()), traceId()));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable("id") Long id,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        cartService.removeItem(fanId, id);
        return ResponseEntity.noContent().build();
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
