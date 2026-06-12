package com.fandrops.community.api.feed;

import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.api.Principal;
import com.fandrops.community.application.feed.FeedLikeService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feeds/{feedId}/likes")
public class FeedLikeController extends CommunityControllerSupport {

    private final FeedLikeService feedLikeService;

    public FeedLikeController(FeedLikeService feedLikeService, Environment environment) {
        super(environment);
        this.feedLikeService = feedLikeService;
    }

    // POST /api/v1/feeds/{feedId}/likes
    @PostMapping
    public ResponseEntity<Void> likeFeed(
            @PathVariable Long feedId,
            @RequestHeader(value = "X-Artist-Id") Long artistId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Principal principal = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        feedLikeService.likeFeed(feedId, principal.fanId(), principal.artistMemberId(), artistId);
        return ResponseEntity.status(201).build();
    }

    // DELETE /api/v1/feeds/{feedId}/likes
    @DeleteMapping
    public ResponseEntity<Void> unlikeFeed(
            @PathVariable Long feedId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Principal principal = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        feedLikeService.unlikeFeed(feedId, principal.fanId(), principal.artistMemberId());
        return ResponseEntity.noContent().build();
    }

}