package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentAlreadyFailedException;
import com.fandrops.payment.application.payment.PaymentAmountMismatchException;
import com.fandrops.payment.application.payment.PaymentConfirmFailedException;
import com.fandrops.payment.application.payment.PaymentNotFoundException;
import com.fandrops.payment.application.payment.PaymentLockConflictException;
import com.fandrops.payment.application.payment.TossAuthenticationException;
import com.fandrops.payment.application.payment.TossPaymentUnavailableException;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PaymentController.class)
public class PaymentControllerAdvice {

    record ErrorDetail(String code, String message, boolean retryable) {}

    record ErrorEnvelope(Object data, ErrorDetail error) {
        static ErrorEnvelope of(String code, String message, boolean retryable) {
            return new ErrorEnvelope(null, new ErrorDetail(code, message, retryable));
        }
    }

    @ExceptionHandler(PaymentAmountMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handle(PaymentAmountMismatchException e) {
        return ResponseEntity.status(400)
                .body(ErrorEnvelope.of("AMOUNT_MISMATCH", e.getMessage(), false));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorEnvelope> handle(PaymentNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ErrorEnvelope.of("ORDER_NOT_FOUND", e.getMessage(), false));
    }

    @ExceptionHandler(PaymentAlreadyFailedException.class)
    public ResponseEntity<ErrorEnvelope> handle(PaymentAlreadyFailedException e) {
        return ResponseEntity.status(409)
                .body(ErrorEnvelope.of("DUPLICATE_PAYMENT", e.getMessage(), false));
    }

    @ExceptionHandler(PaymentConfirmFailedException.class)
    public ResponseEntity<ErrorEnvelope> handle(PaymentConfirmFailedException e) {
        String code = e.getErrorCode() != null ? e.getErrorCode() : "PAYMENT_FAILED";
        return ResponseEntity.status(402)
                .body(ErrorEnvelope.of(code, e.getMessage(), false));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handle(IllegalArgumentException e) {
        return ResponseEntity.status(400)
                .body(ErrorEnvelope.of("INVALID_REQUEST", e.getMessage(), false));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(ErrorEnvelope.of("INVALID_REQUEST", message, false));
    }

    @ExceptionHandler(TossPaymentUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handle(TossPaymentUnavailableException e) {
        return ResponseEntity.status(503)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", e.getMessage(), true));
    }

    @ExceptionHandler(TossAuthenticationException.class)
    public ResponseEntity<ErrorEnvelope> handle(TossAuthenticationException e) {
        return ResponseEntity.status(500)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", e.getMessage(), false));
    }

    @ExceptionHandler(PaymentLockConflictException.class)
    public ResponseEntity<ErrorEnvelope> handle(PaymentLockConflictException e) {
        return ResponseEntity.status(503)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", "일시적으로 처리할 수 없습니다. 잠시 후 재시도해 주세요.", true));
    }
}