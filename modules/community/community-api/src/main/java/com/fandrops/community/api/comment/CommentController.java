package com.fandrops.community.api.comment;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.api.Principal;
import com.fandrops.community.application.comment.CommentCreateCommand;
import com.fandrops.community.application.comment.CommentListResult;
import com.fandrops.community.application.comment.CommentResult;
import com.fandrops.community.application.comment.CommentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/feeds/{feedId}/comments")
public class CommentController extends CommunityControllerSupport {

    private final CommentService commentService;

    public CommentController(CommentService commentService, Environment environment) {
        super(environment);
        this.commentService = commentService;
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

        Principal principal = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        CommentResult result = commentService.createComment(new CommentCreateCommand(
                feedId, artistId, principal.fanId(), principal.artistMemberId(), request.parentId(), request.content()));
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(Map.of("commentId", result.id()), traceId()));
    }

    // GET /api/v1/feeds/{feedId}/comments — 최상위 댓글 커서 페이징 + 대댓글 포함
    @GetMapping
    public ResponseEntity<ApiResponse<CommentListResult>> getComments(
            @PathVariable Long feedId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {

        CommentListResult result = commentService.getComments(feedId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

}