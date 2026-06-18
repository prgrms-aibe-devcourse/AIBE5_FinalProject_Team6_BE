package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistMemberResponse;
import com.fandrops.user.api.dto.CreateArtistMemberRequest;
import com.fandrops.user.api.dto.UpdateArtistMemberRequest;
import com.fandrops.user.api.dto.UpdateProfileImageRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.service.ArtistMemberService;
import com.fandrops.user.domain.ArtistMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArtistMemberController extends UserControllerSupport {

    private final ArtistMemberService artistMemberService;

    public ArtistMemberController(ArtistMemberService artistMemberService,
                                   Environment environment) {
        super(environment);
        this.artistMemberService = artistMemberService;
    }

    // F03-01: Agency가 소속 아티스트의 멤버 계정 생성
    @PreAuthorize("hasRole('AGENCY')")
    @PostMapping("/api/v1/artist-members")
    public ResponseEntity<ApiResponse<ArtistMemberResponse>> create(
            @Valid @RequestBody CreateArtistMemberRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistMember created = artistMemberService.createArtistMember(
                new CreateArtistMemberCommand(
                        request.artistId(),
                        request.loginId(),
                        request.rawPassword(),
                        request.memberName()),
                agencyId,
                resolveClientIp(httpRequest),
                traceId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ArtistMemberResponse.from(created), traceId()));
    }

    @PreAuthorize("hasRole('AGENCY')")
    @GetMapping("/api/v1/artist-members/{id}")
    public ResponseEntity<ApiResponse<ArtistMemberResponse>> get(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistMember member = artistMemberService.getArtistMember(
                id, agencyId, resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(ArtistMemberResponse.from(member), traceId()));
    }

    @PreAuthorize("hasRole('AGENCY')")
    @PatchMapping("/api/v1/artist-members/{id}")
    public ResponseEntity<ApiResponse<ArtistMemberResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateArtistMemberRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistMember updated = artistMemberService.updateArtistMemberName(
                id, request.memberName(), agencyId, resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(ArtistMemberResponse.from(updated), traceId()));
    }

    @PreAuthorize("hasRole('AGENCY')")
    @DeleteMapping("/api/v1/artist-members/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        artistMemberService.deleteArtistMember(id, agencyId, resolveClientIp(httpRequest), traceId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('AGENCY')")
    @PostMapping("/api/v1/artist-members/{id}/profile-image/presigned-url")
    public ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> generateProfileImagePresignedUrl(
            @PathVariable Long id,
            @Valid @RequestBody UploadPresignedUrlRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        PresignedUploadResult result = artistMemberService.generateProfileImagePresignedUrl(
                id, agencyId, request.contentType(), request.contentLength(),
                resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(UploadPresignedUrlResponse.from(result), traceId()));
    }

    @PreAuthorize("hasRole('AGENCY')")
    @PatchMapping("/api/v1/artist-members/{id}/profile-image")
    public ResponseEntity<ApiResponse<ArtistMemberResponse>> updateProfileImage(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProfileImageRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        Long agencyId = resolveAgencyId(authentication);
        ArtistMember updated = artistMemberService.updateProfileImageUrl(
                id, request.imageUrl(), agencyId, resolveClientIp(httpRequest), traceId());
        return ResponseEntity.ok(ApiResponse.ok(ArtistMemberResponse.from(updated), traceId()));
    }
}