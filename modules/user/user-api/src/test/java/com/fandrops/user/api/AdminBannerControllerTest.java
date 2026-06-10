package com.fandrops.user.api;

import com.fandrops.user.api.dto.CreateBannerRequest;
import com.fandrops.user.api.dto.UpdateBannerRequest;
import com.fandrops.user.application.dto.BannerResult;
import com.fandrops.user.application.dto.CreateBannerCommand;
import com.fandrops.user.application.dto.UpdateBannerCommand;
import com.fandrops.user.application.service.BannerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBannerControllerTest {

    @Mock BannerService bannerService;
    @Mock Environment environment;

    AdminBannerController controller;

    BannerResult stub;

    @BeforeEach
    void setUp() {
        controller = new AdminBannerController(bannerService, environment);
        stub = new BannerResult(1L, "배너 제목", "img.jpg", "https://fandrops.com", 1, true, null, null);
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
        CreateBannerRequest request = new CreateBannerRequest("배너 제목", "img.jpg", "https://fandrops.com", 1, null, null);
        when(bannerService.createBanner(any(CreateBannerCommand.class))).thenReturn(stub);

        ResponseEntity<?> response = controller.create(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(bannerService).createBanner(any(CreateBannerCommand.class));
    }

    @Test
    @DisplayName("배너 수정 → 200 OK")
    void update_success_returns200() {
        UpdateBannerRequest request = new UpdateBannerRequest("새 제목", null, null, null, null, null, null);
        when(bannerService.updateBanner(eq(1L), any(UpdateBannerCommand.class))).thenReturn(stub);

        ResponseEntity<?> response = controller.update(1L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bannerService).updateBanner(eq(1L), any(UpdateBannerCommand.class));
    }

    @Test
    @DisplayName("배너 삭제 → 204 No Content")
    void delete_success_returns204() {
        ResponseEntity<?> response = controller.delete(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(bannerService).deleteBanner(1L);
    }
}
