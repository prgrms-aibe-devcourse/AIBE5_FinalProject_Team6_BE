package com.fandrops.payment.api.payment;

import com.fandrops.common.ApiResponse;
import com.fandrops.payment.api.queue.SseCapacityExceededException;
import com.fandrops.payment.application.payment.PaymentAlreadyFailedException;
import com.fandrops.payment.application.payment.PaymentAmountMismatchException;
import com.fandrops.payment.application.payment.PaymentConfirmFailedException;
import com.fandrops.payment.application.payment.PaymentConfirmTimeoutException;
import com.fandrops.payment.application.payment.PaymentLockConflictException;
import com.fandrops.payment.application.payment.PaymentNotFoundException;
import com.fandrops.payment.application.payment.TossAuthenticationException;
import com.fandrops.payment.application.payment.TossPaymentUnavailableException;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.fandrops.payment.api")
public class PaymentControllerAdvice {

    @ExceptionHandler(PaymentAmountMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentAmountMismatchException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("AMOUNT_MISMATCH", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("ORDER_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(PaymentAlreadyFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentAlreadyFailedException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("DUPLICATE_PAYMENT", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(PaymentConfirmFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentConfirmFailedException e) {
        String code = e.getErrorCode() != null ? e.getErrorCode() : "PAYMENT_FAILED";
        return ResponseEntity.status(402)
                .body(ApiResponse.fail(code, e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handle(IllegalArgumentException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", message, false, traceId()));
    }

    @ExceptionHandler(PaymentConfirmTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentConfirmTimeoutException e) {
        return ResponseEntity.status(408)
                .body(ApiResponse.fail("PAYMENT_CONFIRM_TIMEOUT", e.getMessage(), true, traceId()));
    }

    @ExceptionHandler(TossPaymentUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handle(TossPaymentUnavailableException e) {
        return ResponseEntity.status(503)
                .body(ApiResponse.fail("INTERNAL_ERROR", e.getMessage(), true, traceId()));
    }

    @ExceptionHandler(TossAuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handle(TossAuthenticationException e) {
        return ResponseEntity.status(500)
                .body(ApiResponse.fail("INTERNAL_ERROR", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(PaymentLockConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handle(PaymentLockConflictException e) {
        return ResponseEntity.status(503)
                .body(ApiResponse.fail("INTERNAL_ERROR", "일시적으로 처리할 수 없습니다. 잠시 후 재시도해 주세요.", true, traceId()));
    }

    @ExceptionHandler(SseCapacityExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handle(SseCapacityExceededException e) {
        // Accept: text/event-stream 요청에서도 JSON 429가 정상 전달되도록 Content-Type 명시
        // (명시하지 않으면 콘텐츠 협상 실패 → HttpMediaTypeNotAcceptableException → 406 → /error 디스패치 → Security denyAll → 403)
        return ResponseEntity.status(429)
                .header("Retry-After", "60")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.fail("RATE_LIMITED", e.getMessage(), true, traceId()));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}