package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentDetailResult;
import com.fandrops.payment.application.payment.PaymentQueryService;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/fans/me/orders/{orderId}/payment — orderId로 결제 상세 조회 (F07-02 보완). */
@RestController
@RequestMapping("/api/v1/fans/me/orders")
public class FanOrderPaymentController {

    private final PaymentQueryService paymentQueryService;
    private final Environment environment;

    public FanOrderPaymentController(PaymentQueryService paymentQueryService, Environment environment) {
        this.paymentQueryService = paymentQueryService;
        this.environment = environment;
    }

    @GetMapping("/{orderId}/payment")
    public ResponseEntity<PaymentDetailResponse> getPaymentByOrder(
            @PathVariable Long orderId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        PaymentDetailResult result = paymentQueryService.getDetailByOrderId(orderId, fanId);
        return ResponseEntity.ok(PaymentDetailResponse.from(result));
    }

    private Long resolveFanId(Authentication authentication, Long fanIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return fanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long fanId) {
            return fanId;
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}