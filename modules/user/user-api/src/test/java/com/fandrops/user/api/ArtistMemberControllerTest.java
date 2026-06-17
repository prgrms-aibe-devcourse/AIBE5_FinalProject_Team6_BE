package com.fandrops.user.api;

import com.fandrops.user.api.dto.CreateArtistMemberRequest;
import com.fandrops.user.api.dto.UpdateArtistMemberRequest;
import com.fandrops.user.api.dto.UpdateProfileImageRequest;
import com.fandrops.user.api.dto.UploadPresignedUrlRequest;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.ArtistMemberNotFoundException;
import com.fandrops.user.application.service.ArtistMemberService;
import com.fandrops.user.domain.ArtistMember;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistMemberControllerTest {

    @Mock ArtistMemberService artistMemberService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    ArtistMemberController controller;

    private static final Long AGENCY_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new ArtistMemberController(artistMemberService, environment);
    }

    private void givenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(AGENCY_ID);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    private ArtistMember dummyMember() {
        return ArtistMember.builder()
                .id(1L).artistId(1L).loginId("hani").passwordHash("hash").memberName("하니").build();
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 생성 → 201 Created")
    void create_success_returns201() {
        givenAuthenticated();
        when(artistMemberService.createArtistMember(any(), anyLong(), anyString(), anyString()))
                .thenReturn(dummyMember());

        ResponseEntity<?> response = controller.create(
                new CreateArtistMemberRequest(1L, "hani", "pass", "하니"),
                authentication, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @DisplayName("소유권 불일치 아티스트로 멤버 생성 → ArtistNotFoundException 전파")
    void create_ownershipMismatch_propagatesArtistNotFoundException() {
        givenAuthenticated();
        when(artistMemberService.createArtistMember(any(), anyLong(), anyString(), anyString()))
                .thenThrow(new com.fandrops.user.application.exception.ArtistNotFoundException("존재하지 않는 아티스트 그룹입니다."));

        assertThrows(com.fandrops.user.application.exception.ArtistNotFoundException.class,
                () -> controller.create(
                        new CreateArtistMemberRequest(99L, "hani", "pass", "하니"),
                        authentication, httpRequest));
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 조회 → 200 OK")
    void get_success_returns200() {
        givenAuthenticated();
        when(artistMemberService.getArtistMember(eq(1L), anyLong(), anyString(), anyString()))
                .thenReturn(dummyMember());

        ResponseEntity<?> response = controller.get(1L, authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("소유권 불일치 멤버 조회 → ArtistMemberNotFoundException 전파")
    void get_ownershipMismatch_propagatesNotFoundException() {
        givenAuthenticated();
        when(artistMemberService.getArtistMember(eq(99L), anyLong(), anyString(), anyString()))
                .thenThrow(new ArtistMemberNotFoundException("존재하지 않는 아티스트 멤버입니다."));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> controller.get(99L, authentication, httpRequest));
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 이름 수정 → 200 OK")
    void update_success_returns200() {
        givenAuthenticated();
        when(artistMemberService.updateArtistMemberName(eq(1L), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(dummyMember());

        ResponseEntity<?> response = controller.update(
                1L, new UpdateArtistMemberRequest("다니"), authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("소유권 불일치 멤버 이름 수정 → ArtistMemberNotFoundException 전파")
    void update_ownershipMismatch_propagatesNotFoundException() {
        givenAuthenticated();
        when(artistMemberService.updateArtistMemberName(eq(99L), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new ArtistMemberNotFoundException("존재하지 않는 아티스트 멤버입니다."));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> controller.update(99L, new UpdateArtistMemberRequest("다니"), authentication, httpRequest));
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("멤버 삭제 → 204 No Content")
    void delete_success_returns204() {
        givenAuthenticated();
        ResponseEntity<?> response = controller.delete(1L, authentication, httpRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(artistMemberService).deleteArtistMember(eq(1L), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("소유권 불일치 멤버 삭제 → ArtistMemberNotFoundException 전파")
    void delete_ownershipMismatch_propagatesNotFoundException() {
        givenAuthenticated();
        org.mockito.Mockito.doThrow(new ArtistMemberNotFoundException("존재하지 않는 아티스트 멤버입니다."))
                .when(artistMemberService).deleteArtistMember(eq(99L), anyLong(), anyString(), anyString());

        assertThrows(ArtistMemberNotFoundException.class,
                () -> controller.delete(99L, authentication, httpRequest));
    }

    // ── Profile Image Presigned URL ──────────────────────────────────────────

    @Test
    @DisplayName("프로필 이미지 Presigned URL 발급 → 200 OK")
    void generateProfileImagePresignedUrl_success_returns200() {
        givenAuthenticated();
        PresignedUploadResult stub = new PresignedUploadResult(
                "https://s3.presigned.url", "https://cdn.fandrops.com/img.jpg",
                Instant.now().plusSeconds(600));
        when(artistMemberService.generateProfileImagePresignedUrl(
                eq(1L), anyLong(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(stub);

        ResponseEntity<?> response = controller.generateProfileImagePresignedUrl(
                1L, new UploadPresignedUrlRequest("image/jpeg", 1024L),
                authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(artistMemberService).generateProfileImagePresignedUrl(
                eq(1L), eq(AGENCY_ID), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("소유권 불일치 멤버 Presigned URL 발급 → ArtistMemberNotFoundException 전파")
    void generateProfileImagePresignedUrl_ownershipMismatch_propagatesNotFoundException() {
        givenAuthenticated();
        when(artistMemberService.generateProfileImagePresignedUrl(
                eq(99L), anyLong(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new ArtistMemberNotFoundException("존재하지 않는 아티스트 멤버입니다."));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> controller.generateProfileImagePresignedUrl(
                        99L, new UploadPresignedUrlRequest("image/jpeg", 1024L),
                        authentication, httpRequest));
    }

    // ── Profile Image URL 확정 저장 ──────────────────────────────────────────

    @Test
    @DisplayName("프로필 이미지 URL 저장 → 200 OK, 업데이트된 멤버 반환")
    void updateProfileImage_success_returns200() {
        givenAuthenticated();
        ArtistMember withImage = ArtistMember.builder()
                .id(1L).artistId(1L).loginId("hani").passwordHash("hash").memberName("하니")
                .profileImageUrl("https://cdn.fandrops.com/img.jpg").build();
        when(artistMemberService.updateProfileImageUrl(
                eq(1L), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(withImage);

        ResponseEntity<?> response = controller.updateProfileImage(
                1L, new UpdateProfileImageRequest("https://cdn.fandrops.com/img.jpg"),
                authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(artistMemberService).updateProfileImageUrl(
                eq(1L), eq("https://cdn.fandrops.com/img.jpg"), eq(AGENCY_ID), anyString(), anyString());
    }

    @Test
    @DisplayName("소유권 불일치 멤버 이미지 URL 저장 → ArtistMemberNotFoundException 전파")
    void updateProfileImage_ownershipMismatch_propagatesNotFoundException() {
        givenAuthenticated();
        when(artistMemberService.updateProfileImageUrl(
                eq(99L), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new ArtistMemberNotFoundException("존재하지 않는 아티스트 멤버입니다."));

        assertThrows(ArtistMemberNotFoundException.class,
                () -> controller.updateProfileImage(
                        99L, new UpdateProfileImageRequest("https://cdn.fandrops.com/img.jpg"),
                        authentication, httpRequest));
    }
}
