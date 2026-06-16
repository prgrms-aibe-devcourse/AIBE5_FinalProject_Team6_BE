package com.fandrops.order.api;

import com.fandrops.order.api.dto.ApiError;
import com.fandrops.order.api.dto.ApiResponse;
import com.fandrops.order.domain.exception.AccessTicketInvalidException;
import com.fandrops.order.domain.exception.OrderCancellationNotAllowedException;
import com.fandrops.order.domain.exception.StoreBannerNotFoundException;
import com.fandrops.order.domain.exception.CartAccessDeniedException;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.exception.RestockAlertNotFoundException;
import com.fandrops.order.domain.exception.CartItemNotFoundException;
import com.fandrops.order.domain.exception.CartNotFoundException;
import com.fandrops.order.domain.exception.OrderNotFoundException;
import com.fandrops.order.domain.exception.OutOfStockException;
import com.fandrops.order.domain.exception.ReserveConflictException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** order-api 예외 → HTTP 응답 변환 핸들러. */
@RestControllerAdvice(basePackages = "com.fandrops.order.api")
public class OrderExceptionHandler {

    @ExceptionHandler(AccessTicketInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessTicketInvalid(AccessTicketInvalidException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        new ApiError("INVALID_QUEUE_TICKET", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(OutOfStockException.class)
    public ResponseEntity<ApiResponse<Void>> handleOutOfStock(OutOfStockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        new ApiError("OUT_OF_STOCK", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ReserveConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleReserveConflict(ReserveConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        new ApiError("RESERVE_FAILED", e.getMessage(), true),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(OrderCancellationNotAllowedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderCancellationNotAllowed(OrderCancellationNotAllowedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(
                        new ApiError("ORDER_CANCELLATION_NOT_ALLOWED", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotFound(OrderNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("ORDER_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        new ApiError("INVALID_REQUEST", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ApiResponse<Void>> handleNumberFormat(NumberFormatException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        new ApiError("INVALID_REQUEST", "유효하지 않은 인증 정보입니다.", false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProductNotFound(ProductNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("PRODUCT_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(RestockAlertNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleRestockAlertNotFound(RestockAlertNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("RESTOCK_ALERT_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(CartAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartAccessDenied(CartAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        new ApiError("CART_ACCESS_DENIED", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(CartNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartNotFound(CartNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("CART_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(CartItemNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCartItemNotFound(CartItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("CART_ITEM_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }

    @ExceptionHandler(StoreBannerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoreBannerNotFound(StoreBannerNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(
                        new ApiError("STORE_BANNER_NOT_FOUND", e.getMessage(), false),
                        MDC.get("traceId") != null ? MDC.get("traceId") : UUID.randomUUID().toString()));
    }
}
