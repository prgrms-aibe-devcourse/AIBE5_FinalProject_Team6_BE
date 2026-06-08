package com.fandrops.community.api.artist;

import com.fandrops.common.ApiResponse;
import com.fandrops.community.api.CommunityControllerSupport;
import com.fandrops.community.application.follow.FanJoinResult;
import com.fandrops.community.application.follow.FanJoinService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/artists/{artistId}/follow")
public class ArtistFollowController extends CommunityControllerSupport {

    private final FanJoinService fanJoinService;

    public ArtistFollowController(FanJoinService fanJoinService, Environment environment) {
        super(environment);
        this.fanJoinService = fanJoinService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FanJoinResult>> follow(
            @PathVariable Long artistId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        assertFanRole(authentication);
        Long fanId = resolveFanId(authentication, fanIdHeader);
        FanJoinResult result = fanJoinService.join(artistId, fanId);
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(result, traceId()));
    }

    @DeleteMapping
    public ResponseEntity<Void> unfollow(
            @PathVariable Long artistId,
            Authentication authentication,
            @RequestHeader(value = "X-Fan-Id", required = false) Long fanIdHeader) {

        assertFanRole(authentication);
        Long fanId = resolveFanId(authentication, fanIdHeader);
        fanJoinService.leave(artistId, fanId);
        return ResponseEntity.noContent().build();
    }
}