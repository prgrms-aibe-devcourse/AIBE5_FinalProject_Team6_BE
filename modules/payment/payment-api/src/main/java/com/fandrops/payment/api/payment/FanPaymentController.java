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

/** GET /api/v1/fans/me/payments/{id} — 인증된 팬 본인의 결제 상세 조회 (F07-02). */
@RestController
@RequestMapping("/api/v1/fans/me/payments")
public class FanPaymentController {

    private final PaymentQueryService paymentQueryService;
    private final Environment environment;

    public FanPaymentController(PaymentQueryService paymentQueryService, Environment environment) {
        this.paymentQueryService = paymentQueryService;
        this.environment = environment;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDetailResponse> getDetail(
            @PathVariable Long id,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {
        Long fanId = resolveFanId(authentication, fanIdHeader);
        PaymentDetailResult result = paymentQueryService.getDetail(id, fanId);
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