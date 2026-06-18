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
            } else if (hasFanRole(authentication)) {
                viewerFanId = Long.parseLong(authentication.getName());
            } else {
                throw new IllegalStateException("지원하지 않는 role: " + authentication.getAuthorities());
            }
        }

        FeedListResult result = feedService.getFeeds(artistId, cursor, size, viewerFanId, viewerArtistMemberId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

    // GET /api/v1/artists/{artistId}/feeds/{feedId} — 피드 단건 상세 조회
    @GetMapping("/{feedId}")
    public ResponseEntity<ApiResponse<FeedResult>> getFeed(
            @PathVariable Long artistId,
            @PathVariable Long feedId,
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
            } else if (hasFanRole(authentication)) {
                viewerFanId = Long.parseLong(authentication.getName());
            } else {
                throw new IllegalStateException("지원하지 않는 role: " + authentication.getAuthorities());
            }
        }

        FeedResult result = feedService.getFeed(feedId, viewerFanId, viewerArtistMemberId);
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

}