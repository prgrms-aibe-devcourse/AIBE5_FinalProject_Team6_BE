package com.fandrops.notification;

import com.fandrops.common.ApiResponse;
import com.fandrops.notification.application.exception.NotificationNotFoundException;
import com.fandrops.notification.application.exception.UnauthenticatedException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.fandrops.notification")
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotificationNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOTIFICATION_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthenticatedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("UNAUTHORIZED", e.getMessage(), false, traceId()));
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : java.util.UUID.randomUUID().toString();
    }
}