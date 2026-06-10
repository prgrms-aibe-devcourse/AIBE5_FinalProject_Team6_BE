package com.fandrops.user.api;

import com.fandrops.user.application.dto.BannerResult;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BannerControllerTest {

    @Mock BannerService bannerService;
    @Mock Environment environment;

    BannerController controller;

    @BeforeEach
    void setUp() {
        controller = new BannerController(bannerService, environment);
    }

    @Test
    @DisplayName("활성 메인 배너 조회 → 200 OK, 목록 반환")
    void getMainBanners_success_returns200WithActiveList() {
        BannerResult stub = new BannerResult(1L, "이벤트 배너", "img.jpg", "https://fandrops.com", 1, true, null, null);
        when(bannerService.getActiveMainBanners()).thenReturn(List.of(stub));

        ResponseEntity<?> response = controller.getMainBanners();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bannerService).getActiveMainBanners();
    }

    @Test
    @DisplayName("활성 배너 없을 때 → 200 OK, 빈 목록 반환")
    void getMainBanners_emptyList_returns200() {
        when(bannerService.getActiveMainBanners()).thenReturn(List.of());

        ResponseEntity<?> response = controller.getMainBanners();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(bannerService).getActiveMainBanners();
    }
}
