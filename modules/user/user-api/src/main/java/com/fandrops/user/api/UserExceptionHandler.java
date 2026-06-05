package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.application.exception.DuplicateEmailException;
import com.fandrops.user.application.exception.FanNotFoundException;
import com.fandrops.user.application.exception.InvalidCredentialsException;
import com.fandrops.user.application.exception.InvalidTokenException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.fandrops.user.api")
public class UserExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateEmail(DuplicateEmailException e) {
        return ApiResponse.fail("DUPLICATE_EMAIL", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidCredentials(InvalidCredentialsException e) {
        return ApiResponse.fail("INVALID_CREDENTIALS", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleInvalidToken(InvalidTokenException e) {
        return ApiResponse.fail("INVALID_TOKEN", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(FanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleFanNotFound(FanNotFoundException e) {
        return ApiResponse.fail("FAN_NOT_FOUND", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId());
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "";
    }
}
