package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentWebhookCommand;
import com.fandrops.payment.application.payment.PaymentWebhookService;
import com.fandrops.payment.application.payment.TossWebhookVerifyPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
        TossWebhookRequest request;
        try {
            request = objectMapper.readValue(rawBody, TossWebhookRequest.class);
        } catch (JsonProcessingException e) {
            log.error("웹훅 JSON 파싱 오류", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!"PAYMENT_STATUS_CHANGED".equals(request.getEventType())) {
            log.info("처리하지 않는 웹훅 eventType 무시: {}", request.getEventType());
            return ResponseEntity.ok().build();
        }
        TossWebhookRequest.Data data = request.getData();
        Long orderId;
        try {
            orderId = Long.parseLong(data.getOrderId());
        } catch (NumberFormatException e) {
            log.error("orderId 파싱 실패: value={}", data.getOrderId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Instant approvedAt = null;
        if (data.getApprovedAt() != null) {
            try {
                approvedAt = OffsetDateTime.parse(data.getApprovedAt()).toInstant();
            } catch (DateTimeParseException e) {
                log.warn("approvedAt 파싱 실패, null 처리: value={}", data.getApprovedAt());
            }
        }
        paymentWebhookService.handle(new PaymentWebhookCommand(
                data.getPaymentKey(),
                data.getStatus(),
                data.getMethod(),
                data.getTotalAmount(),
                orderId,
                approvedAt));
        return ResponseEntity.ok().build();
    }
}