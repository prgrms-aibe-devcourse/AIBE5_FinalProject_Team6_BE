package com.fandrops.community.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId()));
    }

    private static String traceId() {
        return MDC.get("traceId");
    }
}