package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistProfileListResponse;
import com.fandrops.user.api.dto.ArtistProfileResponse;
import com.fandrops.user.api.dto.UpdateArtistProfileRequest;
import com.fandrops.user.api.dto.UpdateProfileImageRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlResponse;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.exception.InvalidImageUrlException;
import com.fandrops.user.application.service.ArtistProfileService;
import com.fandrops.user.domain.ArtistProfile;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyArtistControllerTest {

    @Mock ArtistProfileService artistProfileService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    AgencyArtistController controller;

    @BeforeEach
    void setUp() {
        controller = new AgencyArtistController(artistProfileService, environment);
    }

    private void givenAuthenticated(Long agencyId) {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(agencyId);
    }

    private ArtistProfile dummyProfile(Long id, Long agencyId) {
        return ArtistProfile.builder()
                .id(id).agencyId(agencyId).name("NOVA")
                .fanCount(100L)
                .joinedAt(LocalDateTime.of(2020, 1, 1, 0, 0))
                .build();
    }

    @Test
    @DisplayName("Agency 소속 아티스트 목록 조회 → 200 OK, items 2건 반환")
    void list_success_returns200WithAgencyArtists() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(
                List.of(dummyProfile(1L, 20L), dummyProfile(2L, 20L)), null, false);
        when(artistProfileService.listByAgencyId(20L, null, 20)).thenReturn(stubResult);

        ResponseEntity<ApiResponse<ArtistProfileListResponse>> response =
                controller.list(null, 20, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArtistProfileListResponse body = response.getBody().data();
        assertEquals(2, body.items().size());
        assertFalse(body.hasMore());
        assertNull(body.nextCursor());
        verify(artistProfileService).listByAgencyId(20L, null, 20);
    }

    @Test
    @DisplayName("Agency 소속 아티스트 없을 때 → 200 OK, 빈 items 반환")
    void list_emptyList_returns200WithEmptyItems() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(List.of(), null, false);
        when(artistProfileService.listByAgencyId(20L, null, 20)).thenReturn(stubResult);

        ResponseEntity<ApiResponse<ArtistProfileListResponse>> response =
                controller.list(null, 20, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().data().items().isEmpty());
        assertFalse(response.getBody().data().hasMore());
    }

    @Test
    @DisplayName("cursor 존재 시 → service에 cursor 전달")
    void list_withCursor_passesCursorToService() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(List.of(), null, false);
        when(artistProfileService.listByAgencyId(20L, "1", 20)).thenReturn(stubResult);

        controller.list("1", 20, authentication);

        verify(artistProfileService).listByAgencyId(20L, "1", 20);
    }

    @Test
    @DisplayName("size > 100 → 100으로 clamp")
    void list_oversizedRequest_clampsToMax() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(List.of(), null, false);
        when(artistProfileService.listByAgencyId(20L, null, 100)).thenReturn(stubResult);

        controller.list(null, 200, authentication);

        verify(artistProfileService).listByAgencyId(20L, null, 100);
    }

    @Test
    @DisplayName("size=0 → 1로 clamp")
    void list_zeroSize_clampsToOne() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(List.of(), null, false);
        when(artistProfileService.listByAgencyId(20L, null, 1)).thenReturn(stubResult);

        controller.list(null, 0, authentication);

        verify(artistProfileService).listByAgencyId(20L, null, 1);
    }

    @Test
    @DisplayName("size 음수 → 1로 clamp")
    void list_negativeSize_clampsToOne() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(List.of(), null, false);
        when(artistProfileService.listByAgencyId(20L, null, 1)).thenReturn(stubResult);

        controller.list(null, -5, authentication);

        verify(artistProfileService).listByAgencyId(20L, null, 1);
    }

    @Test
    @DisplayName("hasMore=true 일 때 → nextCursor·hasMore 필드 검증")
    void list_hasMore_returnsNextCursorInBody() {
        givenAuthenticated(20L);
        ArtistProfileListResult stubResult = new ArtistProfileListResult(
                List.of(dummyProfile(1L, 20L)), "1", true);
        when(artistProfileService.listByAgencyId(20L, null, 1)).thenReturn(stubResult);

        ResponseEntity<ApiResponse<ArtistProfileListResponse>> response =
                controller.list(null, 1, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArtistProfileListResponse body = response.getBody().data();
        assertEquals(1, body.items().size());
        assertTrue(body.hasMore());
        assertEquals("1", body.nextCursor());
    }

    // ── PATCH /api/v1/agency/artists/{id} ────────────────────────────────────

    @Test
    @DisplayName("아티스트 프로필 수정 성공 → 200 OK, 수정된 프로필 반환")
    void update_success_returns200() {
        givenAuthenticated(20L);
        ArtistProfile updated = dummyProfile(1L, 20L);
        when(artistProfileService.updateArtistProfile(
                eq(1L), eq(20L), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);
        UpdateArtistProfileRequest request = new UpdateArtistProfileRequest(
                "새 바이오", null, null, null, null, null);

        ResponseEntity<ApiResponse<ArtistProfileResponse>> response =
                controller.update(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().data());
    }

    @Test
    @DisplayName("소유권 불일치 → ArtistNotFoundException 전파")
    void update_agencyMismatch_propagatesArtistNotFoundException() {
        givenAuthenticated(20L);
        when(artistProfileService.updateArtistProfile(
                eq(1L), eq(20L), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ArtistNotFoundException("아티스트를 찾을 수 없습니다."));
        UpdateArtistProfileRequest request = new UpdateArtistProfileRequest(
                "bio", null, null, null, null, null);

        assertThrows(ArtistNotFoundException.class,
                () -> controller.update(1L, request, authentication, httpRequest));
    }

    // ── POST /api/v1/agency/artists/{id}/profile-image/presigned-url ─────────

    @Test
    @DisplayName("presigned URL 발급 성공 → 200 OK, presignedUrl·imageUrl 반환")
    void generateProfileImagePresignedUrl_success_returns200() {
        givenAuthenticated(20L);
        PresignedUploadResult stubResult = new PresignedUploadResult(
                "https://s3.example.com/presigned", "https://s3.example.com/img.jpg",
                Instant.now().plusSeconds(300));
        when(artistProfileService.generateProfileImagePresignedUrl(
                eq(1L), eq(20L), eq("image/jpeg"), eq(1000L), any(), any()))
                .thenReturn(stubResult);
        UploadPresignedUrlRequest request = new UploadPresignedUrlRequest("image/jpeg", 1000L);

        ResponseEntity<ApiResponse<UploadPresignedUrlResponse>> response =
                controller.generateProfileImagePresignedUrl(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://s3.example.com/presigned", response.getBody().data().presignedUrl());
    }

    @Test
    @DisplayName("허용되지 않는 contentType → InvalidContentTypeException 전파")
    void generateProfileImagePresignedUrl_invalidContentType_propagates() {
        givenAuthenticated(20L);
        when(artistProfileService.generateProfileImagePresignedUrl(
                eq(1L), eq(20L), eq("application/pdf"), eq(1000L), any(), any()))
                .thenThrow(new InvalidContentTypeException("application/pdf"));
        UploadPresignedUrlRequest request = new UploadPresignedUrlRequest("application/pdf", 1000L);

        assertThrows(InvalidContentTypeException.class,
                () -> controller.generateProfileImagePresignedUrl(1L, request, authentication, httpRequest));
    }

    // ── PATCH /api/v1/agency/artists/{id}/profile-image ──────────────────────

    @Test
    @DisplayName("이미지 URL 확정 성공 → 200 OK, 업데이트된 프로필 반환")
    void confirmProfileImage_success_returns200() {
        givenAuthenticated(20L);
        ArtistProfile updated = dummyProfile(1L, 20L);
        when(artistProfileService.confirmProfileImageUrl(
                eq(1L), eq(20L), eq("https://cdn.fandrops.com/img.jpg"), any(), any()))
                .thenReturn(updated);
        UpdateProfileImageRequest request = new UpdateProfileImageRequest("https://cdn.fandrops.com/img.jpg");

        ResponseEntity<ApiResponse<ArtistProfileResponse>> response =
                controller.confirmProfileImage(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().data());
    }

    @Test
    @DisplayName("외부 도메인 이미지 URL → InvalidImageUrlException 전파")
    void confirmProfileImage_invalidUrl_propagates() {
        givenAuthenticated(20L);
        when(artistProfileService.confirmProfileImageUrl(
                eq(1L), eq(20L), eq("https://attacker.com/img.jpg"), any(), any()))
                .thenThrow(new InvalidImageUrlException("https://attacker.com/img.jpg"));
        UpdateProfileImageRequest request = new UpdateProfileImageRequest("https://attacker.com/img.jpg");

        assertThrows(InvalidImageUrlException.class,
                () -> controller.confirmProfileImage(1L, request, authentication, httpRequest));
    }
}
