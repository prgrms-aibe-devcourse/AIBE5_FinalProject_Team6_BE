package com.fandrops.community.api.comment;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.application.comment.CommentCreateCommand;
import com.fandrops.community.application.comment.CommentResult;
import com.fandrops.community.application.comment.CommentService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/feeds/{feedId}/comments")
public class CommentController {

    private final CommentService commentService;
    private final Environment environment;

    public CommentController(CommentService commentService, Environment environment) {
        this.commentService = commentService;
        this.environment = environment;
    }

    // POST /api/v1/feeds/{feedId}/comments
    // 팬: X-Fan-Id + X-Artist-Id 필요 / 아티스트 멤버: X-Artist-Member-Id + X-Artist-Id 필요
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createComment(
            @PathVariable Long feedId,
            @RequestHeader("X-Artist-Id") Long artistId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long[] principals = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        Long fanId = principals[0];
        Long artistMemberId = principals[1];

        CommentResult result = commentService.createComment(new CommentCreateCommand(
                feedId, artistId, fanId, artistMemberId, request.parentId(), request.content()));
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(Map.of("commentId", result.id()), traceId()));
    }

    private Long[] resolvePrincipals(Authentication authentication, Long fanIdHeader, Long artistMemberIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return new Long[]{fanIdHeader, null};
        }
        if (artistMemberIdHeader != null && isLocalProfile()) {
            return new Long[]{null, artistMemberIdHeader};
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return new Long[]{Long.parseLong(authentication.getName()), null};
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    private static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
