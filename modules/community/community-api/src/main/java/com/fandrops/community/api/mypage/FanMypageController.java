package com.fandrops.community.api.mypage;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.mypage.ActivityListResult;
import com.fandrops.community.application.mypage.FanActivityService;
import com.fandrops.community.application.mypage.JoinedArtistListResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fans/me")
public class FanMypageController extends CommunityControllerSupport {

    private final FanActivityService fanActivityService;

    public FanMypageController(FanActivityService fanActivityService, Environment environment) {
        super(environment);
        this.fanActivityService = fanActivityService;
    }

    @GetMapping("/activities")
    public ResponseEntity<ApiResponse<ActivityListResult>> getActivities(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        assertFanRole(authentication);
        Long fanId = resolveFanId(authentication, fanIdHeader);
        ActivityListResult result = fanActivityService.getActivities(fanId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }

    @GetMapping("/artists")
    public ResponseEntity<ApiResponse<JoinedArtistListResult>> getJoinedArtists(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        assertFanRole(authentication);
        Long fanId = resolveFanId(authentication, fanIdHeader);
        JoinedArtistListResult result = fanActivityService.getJoinedArtists(fanId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId()));
    }
}