package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.application.exception.AgencyApplicationNotFoundException;
import com.fandrops.user.application.exception.BannerNotFoundException;
import com.fandrops.user.application.exception.DuplicateAgencyAccountException;
import com.fandrops.user.application.exception.DuplicateApplicationException;
import com.fandrops.user.application.exception.DuplicateEmailException;
import com.fandrops.user.application.exception.DuplicateLoginIdException;
import com.fandrops.user.application.exception.DuplicateSocialAccountException;
import com.fandrops.user.application.exception.FanNotFoundException;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.exception.InvalidCredentialsException;
import com.fandrops.user.application.exception.InvalidTokenException;
import com.fandrops.user.application.exception.S3ImageNotFoundException;
import com.fandrops.user.application.exception.S3OperationException;
import com.fandrops.user.domain.AgencyApplicationAlreadyReviewedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.fandrops.user.api")
public class UserExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UserExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ApiResponse.fail("INVALID_REQUEST", message, false, traceId());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateEmail(DuplicateEmailException e) {
        return ApiResponse.fail("DUPLICATE_EMAIL", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(DuplicateSocialAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateSocialAccount(DuplicateSocialAccountException e) {
        return ApiResponse.fail("DUPLICATE_SOCIAL_ACCOUNT", e.getMessage(), false, traceId());
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

    @ExceptionHandler(AgencyApplicationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleAgencyApplicationNotFound(AgencyApplicationNotFoundException e) {
        return ApiResponse.fail("APPLICATION_NOT_FOUND", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(BannerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleBannerNotFound(BannerNotFoundException e) {
        return ApiResponse.fail("BANNER_NOT_FOUND", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateApplication(DuplicateApplicationException e) {
        return ApiResponse.fail("DUPLICATE_APPLICATION", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(DuplicateAgencyAccountException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateAgencyAccount(DuplicateAgencyAccountException e) {
        return ApiResponse.fail("DUPLICATE_AGENCY_ACCOUNT", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(DuplicateLoginIdException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDuplicateLoginId(DuplicateLoginIdException e) {
        return ApiResponse.fail("DUPLICATE_LOGIN_ID", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(AgencyApplicationAlreadyReviewedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleAlreadyReviewed(AgencyApplicationAlreadyReviewedException e) {
        return ApiResponse.fail("ALREADY_REVIEWED", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(InvalidContentTypeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleInvalidContentType(InvalidContentTypeException e) {
        return ApiResponse.fail("INVALID_CONTENT_TYPE", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(S3ImageNotFoundException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleS3ImageNotFound(S3ImageNotFoundException e) {
        return ApiResponse.fail("S3_IMAGE_NOT_FOUND", e.getMessage(), false, traceId());
    }

    @ExceptionHandler(S3OperationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleS3Operation(S3OperationException e) {
        log.error("S3 작업 실패: {}", e.getMessage(), e);
        return ApiResponse.fail("S3_OPERATION_FAILED", "이미지 서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", true, traceId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        return ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId());
    }

    // 카카오·구글 OAuth 서버가 4xx/5xx를 반환한 경우
    // 5xx: 카카오 일시 장애 → retryable=true / 4xx: 코드 만료 등 → retryable=false
    @ExceptionHandler(RestClientResponseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleOAuthProviderError(RestClientResponseException e) {
        log.error("OAuth 제공자 오류: status={} text={}", e.getStatusCode(), e.getStatusText());
        boolean retryable = e.getStatusCode().is5xxServerError();
        return ApiResponse.fail("OAUTH_PROVIDER_ERROR", "소셜 로그인 서버 오류가 발생했습니다.", retryable, traceId());
    }

    // 카카오·구글 OAuth 응답이 예상과 다른 경우 (예: HTTP 200 에러 응답)
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiResponse<Void> handleIllegalState(IllegalStateException e) {
        log.error("OAuth 응답 파싱 오류: {}", e.getMessage());
        return ApiResponse.fail("OAUTH_INVALID_RESPONSE", "소셜 로그인 응답 처리 중 오류가 발생했습니다.", false, traceId());
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}