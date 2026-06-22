package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistProfileListResponse;
import com.fandrops.user.api.dto.ArtistProfileResponse;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.service.ArtistProfileService;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/artists")
public class ArtistProfileController extends UserControllerSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArtistProfileService artistProfileService;

    public ArtistProfileController(ArtistProfileService artistProfileService, Environment environment) {
        super(environment);
        this.artistProfileService = artistProfileService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistProfileResponse>> getArtistProfile(@PathVariable Long id) {
        ArtistProfileResponse response = ArtistProfileResponse.from(artistProfileService.getArtistProfile(id));
        return ResponseEntity.ok(ApiResponse.ok(response, traceId()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ArtistProfileListResponse>> listArtistProfiles(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        ArtistProfileListResult result = artistProfileService.listArtistProfiles(cursor, safeSize);
        return ResponseEntity.ok(ApiResponse.ok(ArtistProfileListResponse.from(result), traceId()));
    }
}