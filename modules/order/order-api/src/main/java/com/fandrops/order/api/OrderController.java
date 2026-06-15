package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.api.dto.CreateOrderRequest;
import com.fandrops.order.api.dto.CreateOrderResponse;
import com.fandrops.order.application.OrderService;
import com.fandrops.order.application.dto.CreateOrderCommand;
import com.fandrops.order.application.dto.CreateOrderResult;
import com.fandrops.order.application.dto.OrderDetailResponse;
import com.fandrops.order.application.dto.OrderItemCommand;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** POST /orders 엔드포인트. fanId는 JWT 또는 로컬 프로필의 X-Fan-Id 헤더로 추출한다. */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final Environment environment;

    public OrderController(OrderService orderService, Environment environment) {
        this.orderService = orderService;
        this.environment = environment;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();

        List<OrderItemCommand> itemCommands = request.getItems().stream()
                .map(item -> new OrderItemCommand(item.getProductId(), item.getQuantity()))
                .toList();

        CreateOrderResult result = orderService.createOrder(
                new CreateOrderCommand(fanId, request.getAccessTicket(), itemCommands));

        CreateOrderResponse response = new CreateOrderResponse(
                result.getOrderId(), result.getStatus(), result.getOrderPaymentKey());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, traceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
            @PathVariable("id") Long orderId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        String traceId = MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString();
        OrderDetailResponse response = orderService.getOrderDetail(orderId, fanId);
        return ResponseEntity.ok(ApiResponse.ok(response, traceId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable("id") Long orderId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        orderService.cancelOrder(orderId, fanId);
        return ResponseEntity.noContent().build();
    }

    // TODO: user 모듈 Auth 계약 확정 후 JWT 클레임에서 fanId 추출로 교체 (표지민 협의)
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
