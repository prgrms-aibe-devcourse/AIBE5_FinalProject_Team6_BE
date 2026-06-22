package com.fandrops.user.api;

import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.exception.ArtistNotFoundException;
import com.fandrops.user.application.service.ArtistMemberService;
import com.fandrops.user.application.service.ArtistProfileService;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArtistProfileControllerTest {

    @Mock ArtistProfileService artistProfileService;
    @Mock ArtistMemberService artistMemberService;
    @Mock Environment environment;

    ArtistProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ArtistProfileController(artistProfileService, artistMemberService, environment);
    }

    private ArtistProfile dummyProfile(Long id) {
        return ArtistProfile.builder()
                .id(id)
                .agencyId(10L)
                .name("NewJeans")
                .fanCount(1000L)
                .joinedAt(LocalDateTime.of(2022, 7, 22, 0, 0))
                .build();
    }

    private ArtistProfileListResult emptyListResult() {
        return new ArtistProfileListResult(List.of(), null, false);
    }

    // ── GET /api/v1/artists/{id} ──────────────────────────────────────────────

    @Test
    @DisplayName("아티스트 프로필 상세 조회 → 200 OK")
    void getArtistProfile_success_returns200() {
        when(artistProfileService.getArtistProfile(1L)).thenReturn(dummyProfile(1L));

        ResponseEntity<?> response = controller.getArtistProfile(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("존재하지 않는 아티스트 조회 → ArtistNotFoundException 전파")
    void getArtistProfile_notFound_throwsArtistNotFoundException() {
        when(artistProfileService.getArtistProfile(99L))
                .thenThrow(new ArtistNotFoundException("존재하지 않는 아티스트입니다."));

        assertThrows(ArtistNotFoundException.class,
                () -> controller.getArtistProfile(99L));
    }

    // ── GET /api/v1/artists ───────────────────────────────────────────────────

    @Test
    @DisplayName("아티스트 목록 기본 조회 → 200 OK, size=20 전달")
    void listArtistProfiles_default_returns200() {
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        when(artistProfileService.listArtistProfiles(any(), sizeCaptor.capture()))
                .thenReturn(emptyListResult());

        ResponseEntity<?> response = controller.listArtistProfiles(null, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(20, sizeCaptor.getValue());
    }

    @Test
    @DisplayName("커서 전달 시 서비스로 그대로 전달")
    void listArtistProfiles_withCursor_passesCursorToService() {
        when(artistProfileService.listArtistProfiles(eq("cursor123"), anyInt()))
                .thenReturn(emptyListResult());

        ResponseEntity<?> response = controller.listArtistProfiles("cursor123", 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(artistProfileService).listArtistProfiles("cursor123", 20);
    }

    @Test
    @DisplayName("size=200 요청 → 100으로 클램프")
    void listArtistProfiles_sizeOver100_clampedTo100() {
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        when(artistProfileService.listArtistProfiles(any(), sizeCaptor.capture()))
                .thenReturn(emptyListResult());

        controller.listArtistProfiles(null, 200);

        assertEquals(100, sizeCaptor.getValue());
    }

    @Test
    @DisplayName("size=0 요청 → 1로 클램프")
    void listArtistProfiles_sizeZero_clampedTo1() {
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        when(artistProfileService.listArtistProfiles(any(), sizeCaptor.capture()))
                .thenReturn(emptyListResult());

        controller.listArtistProfiles(null, 0);

        assertEquals(1, sizeCaptor.getValue());
    }

    // ── GET /api/v1/artists/{artistId}/members (공개) ─────────────────────────

    @Test
    @DisplayName("공개 멤버 목록 조회 성공 → 200 OK, 멤버 2건 반환")
    void listMembers_success_returns200WithMembers() {
        when(artistMemberService.listByArtistIdPublic(1L))
                .thenReturn(List.of(dummyMember(10L), dummyMember(11L)));

        ResponseEntity<?> response = controller.listMembers(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(artistMemberService).listByArtistIdPublic(1L);
    }

    @Test
    @DisplayName("존재하지 않는 아티스트 멤버 목록 조회 → ArtistNotFoundException 전파")
    void listMembers_artistNotFound_propagatesArtistNotFoundException() {
        when(artistMemberService.listByArtistIdPublic(99L))
                .thenThrow(new ArtistNotFoundException("존재하지 않는 아티스트입니다."));

        assertThrows(ArtistNotFoundException.class,
                () -> controller.listMembers(99L));
    }

    @Test
    @DisplayName("멤버 없는 아티스트 → 200 OK, 빈 목록 반환")
    void listMembers_noMembers_returnsEmptyList() {
        when(artistMemberService.listByArtistIdPublic(1L)).thenReturn(List.of());

        ResponseEntity<?> response = controller.listMembers(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    private ArtistMember dummyMember(Long id) {
        return ArtistMember.builder()
                .id(id).artistId(1L).loginId("member" + id)
                .passwordHash("hash").memberName("멤버" + id)
                .role(UserRole.ARTIST).build();
    }
}
