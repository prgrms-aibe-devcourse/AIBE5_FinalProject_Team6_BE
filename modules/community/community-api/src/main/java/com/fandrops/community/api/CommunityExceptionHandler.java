package com.fandrops.community.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.application.exception.AlreadyCheckedInException;
import com.fandrops.community.application.exception.AlreadyJoinedException;
import com.fandrops.community.application.exception.AlreadyLikedException;
import com.fandrops.community.application.exception.AttendanceEventNotFoundException;
import com.fandrops.community.application.exception.ArtistNotFoundException;
import com.fandrops.community.application.exception.DuplicateVoteException;
import com.fandrops.community.application.exception.GoodsVoteClosedException;
import com.fandrops.community.application.exception.GoodsVoteNotFoundException;
import com.fandrops.community.application.exception.CommentNotFoundException;
import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.exception.FeedNotFoundException;
import com.fandrops.community.application.exception.FeedOwnershipException;
import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.exception.LikeNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.exception.UnauthorizedException;
import com.fandrops.community.domain.schedule.exception.ScheduleDomainException;
import com.fandrops.community.domain.vote.exception.GoodsVoteDomainException;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

@RestControllerAdvice
public class CommunityExceptionHandler {

    @ExceptionHandler(AttendanceEventNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> attendanceEventNotFound(AttendanceEventNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("ATTENDANCE_EVENT_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(AlreadyCheckedInException.class)
    public ResponseEntity<ApiResponse<Void>> alreadyCheckedIn(AlreadyCheckedInException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("ALREADY_CHECKED_IN", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(ArtistNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> artistNotFound(ArtistNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("ARTIST_NOT_FOUND", e.getMessage(), false, traceId()));
    }

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

    @ExceptionHandler(LikeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> likeNotFound(LikeNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("LIKE_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(GoodsVoteNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> goodsVoteNotFound(GoodsVoteNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("GOODS_VOTE_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> scheduleNotFound(ScheduleNotFoundException e) {
        return ResponseEntity.status(404)
                .body(ApiResponse.fail("SCHEDULE_NOT_FOUND", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(GoodsVoteClosedException.class)
    public ResponseEntity<ApiResponse<Void>> goodsVoteClosed(GoodsVoteClosedException e) {
        return ResponseEntity.status(422)
                .body(ApiResponse.fail("GOODS_VOTE_CLOSED", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(DuplicateVoteException.class)
    public ResponseEntity<ApiResponse<Void>> duplicateVote(DuplicateVoteException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("DUPLICATE_VOTE", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(AlreadyJoinedException.class)
    public ResponseEntity<ApiResponse<Void>> alreadyJoined(AlreadyJoinedException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("ALREADY_JOINED", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(AlreadyLikedException.class)
    public ResponseEntity<ApiResponse<Void>> alreadyLiked(AlreadyLikedException e) {
        return ResponseEntity.status(409)
                .body(ApiResponse.fail("ALREADY_LIKED", e.getMessage(), false, traceId()));
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

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> forbidden(ForbiddenException e) {
        return ResponseEntity.status(403)
                .body(ApiResponse.fail("FORBIDDEN", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> unauthorized(UnauthorizedException e) {
        return ResponseEntity.status(401)
                .body(ApiResponse.fail("INVALID_TOKEN", e.getMessage(), false, traceId()));
    }

    // Objects.requireNonNull NPE는 컨트롤러 role 검증으로 정상 경로 도달 불가
    // 도달 시 500 반환 의도적 허용

    // JWT sub 클레임이 숫자가 아닌 경우 (토큰 위변조 · 잘못된 발급) -> 401
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ApiResponse<Void>> numberFormat(NumberFormatException e) {
        return ResponseEntity.status(401)
                .body(ApiResponse.fail("INVALID_TOKEN", "인증 토큰이 유효하지 않습니다.", false, traceId()));
    }

    @ExceptionHandler(GoodsVoteDomainException.class)
    public ResponseEntity<ApiResponse<Void>> goodsVoteDomain(GoodsVoteDomainException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId()));
    }

    @ExceptionHandler(ScheduleDomainException.class)
    public ResponseEntity<ApiResponse<Void>> scheduleDomain(ScheduleDomainException e) {
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", e.getMessage(), false, traceId()));
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> methodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "경로 변수 '" + e.getName() + "'의 형식이 올바르지 않습니다.";
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", message, false, traceId()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> missingRequestHeader(MissingRequestHeaderException e) {
        String message = "필수 헤더 '" + e.getHeaderName() + "'가 없습니다.";
        return ResponseEntity.status(400)
                .body(ApiResponse.fail("INVALID_REQUEST", message, false, traceId()));
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}