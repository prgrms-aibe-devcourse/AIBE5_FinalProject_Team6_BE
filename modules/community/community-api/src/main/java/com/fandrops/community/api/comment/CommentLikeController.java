package com.fandrops.community.api.comment;

import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.comment.CommentLikeService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments/{commentId}/likes")
public class CommentLikeController extends CommunityControllerSupport {

    private final CommentLikeService commentLikeService;

    public CommentLikeController(CommentLikeService commentLikeService, Environment environment) {
        super(environment);
        this.commentLikeService = commentLikeService;
    }

    // POST /api/v1/comments/{commentId}/likes — 팬만 가능
    @PostMapping
    public ResponseEntity<Void> likeComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolvePrincipals(authentication, fanIdHeader, null).fanId();
        commentLikeService.likeComment(commentId, fanId);
        return ResponseEntity.status(201).build();
    }

    // DELETE /api/v1/comments/{commentId}/likes
    @DeleteMapping
    public ResponseEntity<Void> unlikeComment(
            @PathVariable Long commentId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        Long fanId = resolvePrincipals(authentication, fanIdHeader, null).fanId();
        commentLikeService.unlikeComment(commentId, fanId);
        return ResponseEntity.noContent().build();
    }
}