package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentWebhookCommand;
import com.fandrops.payment.application.payment.PaymentWebhookService;
import com.fandrops.payment.application.payment.TossWebhookVerifyPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final TossWebhookVerifyPort webhookVerifyPort;
    private final PaymentWebhookService paymentWebhookService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(TossWebhookVerifyPort webhookVerifyPort,
                                     PaymentWebhookService paymentWebhookService,
                                     ObjectMapper objectMapper) {
        this.webhookVerifyPort = webhookVerifyPort;
        this.paymentWebhookService = paymentWebhookService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/v1/payments/toss/webhook
     * Toss PG → 서버 비동기 결제 상태 수신.
     * 멱등: 동일 tossPaymentKey 재수신 시 200 즉시 반환.
     */
    @PostMapping("/toss/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Signature-256", required = false) String signature,
            @RequestBody String rawBody) {
        if (!webhookVerifyPort.verify(rawBody, signature)) {
            log.warn("웹훅 서명 검증 실패");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            TossWebhookRequest request = objectMapper.readValue(rawBody, TossWebhookRequest.class);
            TossWebhookRequest.Data data = request.getData();
            Instant approvedAt = data.getApprovedAt() != null
                    ? OffsetDateTime.parse(data.getApprovedAt()).toInstant()
                    : null;
            paymentWebhookService.handle(new PaymentWebhookCommand(
                    data.getPaymentKey(),
                    data.getStatus(),
                    data.getMethod(),
                    data.getTotalAmount(),
                    Long.parseLong(data.getOrderId()),
                    approvedAt));
        } catch (Exception e) {
            log.error("웹훅 처리 오류", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }
}