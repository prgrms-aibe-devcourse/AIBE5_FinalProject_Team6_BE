package com.fandrops.community.api.feed;

import com.fandrops.community.application.feed.FeedLikeService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/feeds/{feedId}/likes")
public class FeedLikeController {

    private final FeedLikeService feedLikeService;
    private final Environment environment;

    public FeedLikeController(FeedLikeService feedLikeService, Environment environment) {
        this.feedLikeService = feedLikeService;
        this.environment = environment;
    }

    // POST /api/v1/feeds/{feedId}/likes
    @PostMapping
    public ResponseEntity<Void> likeFeed(
            @PathVariable Long feedId,
            @RequestHeader(value = "X-Artist-Id") Long artistId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long[] principals = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        Long fanId = principals[0];
        Long artistMemberId = principals[1];

        feedLikeService.likeFeed(feedId, fanId, artistMemberId, artistId);
        return ResponseEntity.status(201).build();
    }

    // DELETE /api/v1/feeds/{feedId}/likes
    @DeleteMapping
    public ResponseEntity<Void> unlikeFeed(
            @PathVariable Long feedId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long[] principals = resolvePrincipals(authentication, fanIdHeader, artistMemberIdHeader);
        feedLikeService.unlikeFeed(feedId, principals[0], principals[1]);
        return ResponseEntity.noContent().build();
    }

    // [0]=fanId, [1]=artistMemberId — 둘 중 하나만 non-null
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
}