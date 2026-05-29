package com.fandrops.payment.api.payment;

import com.fandrops.payment.application.payment.PaymentAlreadyFailedException;
import com.fandrops.payment.application.payment.PaymentConfirmFailedException;
import com.fandrops.payment.application.payment.PaymentNotFoundException;
import com.fandrops.payment.application.payment.TossPaymentUnavailableException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PaymentController.class)
public class PaymentControllerAdvice {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handle(PaymentNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(PaymentAlreadyFailedException.class)
    public ResponseEntity<Map<String, String>> handle(PaymentAlreadyFailedException e) {
        return ResponseEntity.status(409).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(PaymentConfirmFailedException.class)
    public ResponseEntity<Map<String, String>> handle(PaymentConfirmFailedException e) {
        return ResponseEntity.status(422).body(Map.of(
                "code", e.getErrorCode() != null ? e.getErrorCode() : "UNKNOWN_ERROR",
                "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handle(IllegalArgumentException e) {
        return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handle(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400).body(Map.of("message", message));
    }

    @ExceptionHandler(TossPaymentUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handle(TossPaymentUnavailableException e) {
        return ResponseEntity.status(503).body(Map.of("message", e.getMessage(), "retryable", true));
    }
}