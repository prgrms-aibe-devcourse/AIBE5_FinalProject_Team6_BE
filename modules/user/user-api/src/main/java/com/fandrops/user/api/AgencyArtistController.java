package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistMemberSummaryResponse;
import com.fandrops.user.api.dto.ArtistProfileListResponse;
import com.fandrops.user.api.dto.ArtistProfileResponse;
import com.fandrops.user.api.dto.UpdateArtistProfileRequest;
import com.fandrops.user.api.dto.UpdateProfileImageRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.service.ArtistMemberService;
import com.fandrops.user.application.service.ArtistProfileService;
import com.fandrops.user.domain.ArtistProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agency/artists")
@PreAuthorize("hasRole('AGENCY')")
public class AgencyArtistController extends UserControllerSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private final ArtistProfileService artistProfileService;
    private final ArtistMemberService artistMemberService;

    public AgencyArtistController(ArtistProfileService artistProfileService,
                                   ArtistMemberService artistMemberService,
                                   Environment environment) {
        super(environment);
        this.artistProfileService = artistProfileService;
        this.artistMemberService = artistMemberService;
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

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ArtistProfileResponse>> update(
            @PathVariable Long id,
            @RequestBody UpdateArtistProfileRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistProfile updated = artistProfileService.updateArtistProfile(
                id, agencyId,
                request.bio(), request.profileImageUrl(),
                request.instagramUrl(), request.youtubeUrl(),
                request.twitterUrl(), request.officialUrl(),
                resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(ArtistProfileResponse.from(updated), traceId()));
    }

    @PostMapping("/{id}/profile-image/presigned-url")
    public ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> generateProfileImagePresignedUrl(
            @PathVariable Long id,
            @Valid @RequestBody UploadPresignedUrlRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        PresignedUploadResult result = artistProfileService.generateProfileImagePresignedUrl(
                id, agencyId, request.contentType(), request.contentLength(),
                resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(UploadPresignedUrlResponse.from(result), traceId()));
    }

    @PatchMapping("/{id}/profile-image")
    public ResponseEntity<ApiResponse<ArtistProfileResponse>> confirmProfileImage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileImageRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistProfile updated = artistProfileService.confirmProfileImageUrl(
                id, agencyId, request.imageUrl(),
                resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(ArtistProfileResponse.from(updated), traceId()));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<ArtistMemberSummaryResponse>>> listMembers(
            @PathVariable Long id,
            Authentication authentication) {
        Long agencyId = resolveAgencyId(authentication);
        List<ArtistMemberSummaryResponse> members = artistMemberService.listByArtistId(id, agencyId).stream()
                .map(ArtistMemberSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(members, traceId()));
    }
}
