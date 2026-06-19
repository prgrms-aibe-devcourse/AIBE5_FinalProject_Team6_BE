package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistProfileListResponse;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.service.ArtistProfileService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agency/artists")
@PreAuthorize("hasRole('AGENCY')")
public class AgencyArtistController extends UserControllerSupport {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ArtistProfileService artistProfileService;

    public AgencyArtistController(ArtistProfileService artistProfileService, Environment environment) {
        super(environment);
        this.artistProfileService = artistProfileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ArtistProfileListResponse>> list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Long agencyId = resolveAgencyId(authentication);
        ArtistProfileListResult result = artistProfileService.listByAgencyId(agencyId, cursor, safeSize);
        return ResponseEntity.ok(ApiResponse.ok(ArtistProfileListResponse.from(result), traceId()));
    }
}
