package com.fandrops.community.api.feed;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.feed.FeedCreateCommand;
import com.fandrops.community.application.feed.FeedListResult;
import com.fandrops.community.application.feed.FeedResult;
import com.fandrops.community.application.feed.FeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/artists/{artistId}/feeds")
public class FeedController extends CommunityControllerSupport {

    private final FeedService feedService;

    public FeedController(FeedService feedService, Environment environment) {
        super(environment);
        this.feedService = feedService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Long>>> createFeed(
            @PathVariable Long artistId,
            @Valid @RequestBody FeedCreateRequest request,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        FeedResult result = feedService.createFeed(new FeedCreateCommand(
                artistId, artistMemberId, request.content(), request.imageUrls()));
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(Map.of("feedId", result.id()), traceId()));
    }

    // GET /api/v1/artists/{artistId}/feeds — 커서 페이지네이션
    @GetMapping
    public ResponseEntity<ApiResponse<FeedListResult>> getFeeds(
            @PathVariable Long artistId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long viewerFanId = (fanIdHeader != null && isLocalProfile()) ? fanIdHeader : null;
        Long viewerArtistMemberId = (artistMemberIdHeader != null && isLocalProfile()) ? artistMemberIdHeader : null;
        if (viewerFanId == null && viewerArtistMemberId == null
                && authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (hasArtistOrAgencyRole(authentication)) {
                viewerArtistMemberId = Long.parseLong(authentication.getName());
            } else {
                viewerFanId = Long.parseLong(authentication.getName());
            }
        }

        FeedListResult result = feedService.getFeeds(artistId, cursor, size, viewerFanId, viewerArtistMemberId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

    // DELETE /api/v1/artists/{artistId}/feeds/{feedId} — 피드 삭제 (작성자 아티스트 멤버만)
    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @PathVariable Long feedId,
            Authentication authentication,
            @RequestHeader(value = "X-Artist-Member-Id", required = false) Long artistMemberIdHeader) {

        Long artistMemberId = resolveArtistMemberId(authentication, artistMemberIdHeader);
        feedService.deleteFeed(feedId, artistMemberId);
        return ResponseEntity.noContent().build();
    }

    private static boolean hasArtistOrAgencyRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ARTIST")
                        || a.getAuthority().equals("AGENCY"));
    }

    private Long resolveArtistMemberId(Authentication authentication, Long header) {
        if (header != null && isLocalProfile()) {
            return header;
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Long.parseLong(authentication.getName());
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }
}