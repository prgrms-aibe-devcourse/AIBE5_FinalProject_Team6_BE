package com.fandrops.community.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.FeedOwnershipException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.exception.UnauthorizedException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class CommunityExceptionHandler {

    @ExceptionHandler(FeedNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> feedNotFound(FeedNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("FEED_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> commentNotFound(CommentNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("COMMENT_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(AlreadyLikedException.class)
    public ResponseEntity<ApiResponse<Void>> alreadyLiked(AlreadyLikedException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("ALREADY_LIKED", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(LikeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> likeNotFound(LikeNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("LIKE_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(NotFanMemberException.class)
    public ResponseEntity<ApiResponse<Void>> notFanMember(NotFanMemberException e) {
        return ResponseEntity.status(403)
                .body(ApiResponse.fail("NOT_FAN_MEMBER", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(FeedOwnershipException.class)
    public ResponseEntity<ApiResponse<Void>> feedOwnership(FeedOwnershipException e) {
        return ResponseEntity.status(403)
                .body(ApiResponse.fail("FORBIDDEN", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(401)
                .body(ApiResponse.fail("INVALID_TOKEN", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ApiResponse<Void>> numberFormat(NumberFormatException e) {
        return ResponseEntity.status(401)
                .body(ApiResponse.fail("INVALID_TOKEN", "인증 토큰이 유효하지 않습니다.", false, traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> methodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("요청 형식이 올바르지 않습니다.");
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", message, false, traceId()));
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}