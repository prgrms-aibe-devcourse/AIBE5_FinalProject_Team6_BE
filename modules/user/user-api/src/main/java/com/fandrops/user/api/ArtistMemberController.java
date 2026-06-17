package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistMemberResponse;
import com.fandrops.user.api.dto.CreateArtistMemberRequest;
import com.fandrops.user.application.dto.CreateArtistMemberCommand;
import com.fandrops.user.application.service.ArtistMemberService;
import com.fandrops.user.domain.ArtistMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
}