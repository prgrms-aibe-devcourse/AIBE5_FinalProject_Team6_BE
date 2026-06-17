package com.fandrops.user.api;

import com.fandrops.user.api.dto.CreateBannerRequest;
import com.fandrops.user.api.dto.UpdateBannerRequest;
import com.fandrops.user.application.dto.BannerResult;
import com.fandrops.user.application.dto.CreateBannerCommand;
import com.fandrops.user.application.dto.UpdateBannerCommand;
import com.fandrops.user.application.exception.BannerNotFoundException;
import com.fandrops.user.application.service.BannerService;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBannerControllerTest {

    @Mock BannerService bannerService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    AdminBannerController controller;

    BannerResult stub;

    @BeforeEach
    void setUp() {
        controller = new AdminBannerController(bannerService, environment);
        stub = new BannerResult(1L, null, "배너 제목", "img.jpg", "https://fandrops.com", 1, true, null, null);
    }

    private void givenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    @DisplayName("배너 전체 목록 조회 → 200 OK")
    void list_success_returns200WithItems() {
        when(bannerService.getAllMainBanners()).thenReturn(List.of(stub));

        ResponseEntity<?> response = controller.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bannerService).getAllMainBanners();
    }

    @Test
    @DisplayName("배너 생성 → 201 Created")
    void create_success_returns201() {
        givenAuthenticated();
        CreateBannerRequest request = new CreateBannerRequest("배너 제목", "img.jpg", "https://fandrops.com", 1, null, null);
        when(bannerService.createBanner(any(CreateBannerCommand.class), anyLong(), anyString(), anyString())).thenReturn(stub);

        ResponseEntity<?> response = controller.create(request, authentication, httpRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(bannerService).createBanner(any(CreateBannerCommand.class), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("배너 수정 → 200 OK")
    void update_success_returns200() {
        givenAuthenticated();
        UpdateBannerRequest request = new UpdateBannerRequest("새 제목", null, null, null, null, null, null);
        when(bannerService.updateBanner(eq(1L), any(UpdateBannerCommand.class), anyLong(), anyString(), anyString())).thenReturn(stub);

        ResponseEntity<?> response = controller.update(1L, request, authentication, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bannerService).updateBanner(eq(1L), any(UpdateBannerCommand.class), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("배너 삭제 → 204 No Content")
    void delete_success_returns204() {
        givenAuthenticated();
        ResponseEntity<?> response = controller.delete(1L, authentication, httpRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(bannerService).deleteBanner(eq(1L), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("배너 목록 비어있을 때 → 200 OK, 빈 목록 반환")
    void list_emptyList_returns200() {
        when(bannerService.getAllMainBanners()).thenReturn(List.of());

        ResponseEntity<?> response = controller.list();

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("존재하지 않는 배너 수정 → BannerNotFoundException 전파")
    void update_bannerNotFound_propagatesException() {
        givenAuthenticated();
        UpdateBannerRequest request = new UpdateBannerRequest("새 제목", null, null, null, null, null, null);
        when(bannerService.updateBanner(eq(99L), any(UpdateBannerCommand.class), anyLong(), anyString(), anyString()))
                .thenThrow(new BannerNotFoundException("배너를 찾을 수 없습니다."));

        assertThrows(BannerNotFoundException.class, () -> controller.update(99L, request, authentication, httpRequest));
    }

    @Test
    @DisplayName("존재하지 않는 배너 삭제 → BannerNotFoundException 전파")
    void delete_bannerNotFound_propagatesException() {
        givenAuthenticated();
        doThrow(new BannerNotFoundException("배너를 찾을 수 없습니다."))
                .when(bannerService).deleteBanner(eq(99L), anyLong(), anyString(), anyString());

        assertThrows(BannerNotFoundException.class, () -> controller.delete(99L, authentication, httpRequest));
    }

    @Test
    @DisplayName("배너 생성 — X-Forwarded-For 헤더 존재 시 첫 번째 IP를 사용한다")
    void create_xForwardedForPresent_usesFirstIp() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
        CreateBannerRequest request = new CreateBannerRequest("배너 제목", "img.jpg", "https://fandrops.com", 1, null, null);
        when(bannerService.createBanner(any(CreateBannerCommand.class), anyLong(), anyString(), anyString())).thenReturn(stub);

        controller.create(request, authentication, httpRequest);

        verify(bannerService).createBanner(any(CreateBannerCommand.class), anyLong(), eq("203.0.113.10"), anyString());
    }

    @Test
    @DisplayName("배너 생성 — X-Forwarded-For 없으면 getRemoteAddr() fallback")
    void create_noXForwardedFor_fallsBackToRemoteAddr() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(1L);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        CreateBannerRequest request = new CreateBannerRequest("배너 제목", "img.jpg", "https://fandrops.com", 1, null, null);
        when(bannerService.createBanner(any(CreateBannerCommand.class), anyLong(), anyString(), anyString())).thenReturn(stub);

        controller.create(request, authentication, httpRequest);

        verify(bannerService).createBanner(any(CreateBannerCommand.class), anyLong(), eq("10.0.0.5"), anyString());
    }
}
