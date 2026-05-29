package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentConfirmCommand;
import com.fandrops.payment.application.payment.PaymentConfirmResult;
import com.fandrops.payment.application.payment.PaymentConfirmService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentConfirmService paymentConfirmService;

    public PaymentController(PaymentConfirmService paymentConfirmService) {
        this.paymentConfirmService = paymentConfirmService;
    }

    /**
     * POST /api/v1/payments/toss/confirm
     * 클라이언트 → 서버 → Toss PG confirm 동기 호출.
     * 멱등: 동일 tossPaymentKey 재요청 시 기존 결과 반환 (200).
     */
    @PostMapping("/toss/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(@Valid @RequestBody PaymentConfirmRequest request) {
        PaymentConfirmResult result = paymentConfirmService.confirm(
                new PaymentConfirmCommand(request.getOrderId(), request.getTossPaymentKey(), request.getAmount()));
        return ResponseEntity.ok(PaymentConfirmResponse.from(result));
    }
}