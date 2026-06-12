package com.fandrops.payment.api.payment;

import com.fandrops.common.ApiResponse;
import com.fandrops.payment.api.queue.SseCapacityExceededException;
import com.fandrops.payment.application.payment.PaymentAlreadyFailedException;
import com.fandrops.payment.application.payment.PaymentAmountMismatchException;
import com.fandrops.payment.application.payment.PaymentConfirmFailedException;
import com.fandrops.payment.application.payment.PaymentLockConflictException;
import com.fandrops.payment.application.payment.PaymentNotFoundException;
import com.fandrops.payment.application.payment.TossAuthenticationException;
import com.fandrops.payment.application.payment.TossPaymentUnavailableException;
import java.util.stream.Collectors;
import org.slf4j.MDC;
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
        return ResponseEntity.status(429)
                .header("Retry-After", "60")
                .body(ApiResponse.fail("RATE_LIMITED", e.getMessage(), true, traceId()));
    }

    private String traceId() {
        return MDC.get("traceId");
    }
}