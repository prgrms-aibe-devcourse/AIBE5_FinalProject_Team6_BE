package com.fandrops.community.api.comment;

import com.fandrops.community.application.comment.CommentLikeService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/comments/{commentId}/likes")
public class CommentLikeController {

    private final CommentLikeService commentLikeService;
    private final Environment environment;

    public CommentLikeController(CommentLikeService commentLikeService, Environment environment) {
        this.commentLikeService = commentLikeService;
        this.environment = environment;
    }

    // POST /api/v1/comments/{commentId}/likes — 팬만 가능
    @PostMapping
    public ResponseEntity<Void> likeComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        commentLikeService.likeComment(commentId, fanId);
        return ResponseEntity.status(201).build();
    }

    // DELETE /api/v1/comments/{commentId}/likes
    @DeleteMapping
    public ResponseEntity<Void> unlikeComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolveFanId(authentication, fanIdHeader);
        commentLikeService.unlikeComment(commentId, fanId);
        return ResponseEntity.noContent().build();
    }

    private Long resolveFanId(Authentication authentication, Long header) {
        if (header != null && isLocalProfile()) {
            return header;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}