package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.ArtistProfileListResponse;
import com.fandrops.user.application.dto.ArtistProfileListResult;
import com.fandrops.user.application.service.ArtistProfileService;
import com.fandrops.user.domain.ArtistProfile;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyArtistControllerTest {

    @Mock ArtistProfileService artistProfileService;
    @Mock Environment environment;
    @Mock Authentication authentication;

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
}
