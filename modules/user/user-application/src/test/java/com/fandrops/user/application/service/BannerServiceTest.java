package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.BannerResult;
import com.fandrops.user.application.dto.CreateBannerCommand;
import com.fandrops.user.application.dto.UpdateBannerCommand;
import com.fandrops.user.application.exception.BannerNotFoundException;
import com.fandrops.user.application.port.BannerRepository;
import com.fandrops.user.domain.Banner;
import com.fandrops.user.domain.BannerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BannerServiceTest {

    @Mock BannerRepository bannerRepository;

    BannerService bannerService;

    @BeforeEach
    void setUp() {
        bannerService = new BannerService(bannerRepository);
    }

    private Banner sampleBanner(Long id) {
        return Banner.builder()
                .id(id)
                .bannerType(BannerType.MAIN)
                .title("테스트 배너")
                .imageUrl("https://cdn.fandrops.com/banner.jpg")
                .landingUrl("https://fandrops.com/event")
                .exposureOrder(1)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("활성 메인 배너 조회 — 현재 시각 기준 필터링")
    void getActiveMainBanners_returnsFilteredList() {
        Banner banner = sampleBanner(1L);
        when(bannerRepository.findActiveMainBanners(any(LocalDateTime.class)))
                .thenReturn(List.of(banner));

        List<BannerResult> results = bannerService.getActiveMainBanners();

        assertEquals(1, results.size());
        assertEquals("테스트 배너", results.get(0).title());
    }

    @Test
    @DisplayName("배너 생성 — MAIN 타입으로 저장")
    void createBanner_savesWithMainType() {
        CreateBannerCommand command = new CreateBannerCommand(
                "신규 배너", "https://img.jpg", "https://landing.com", 0, null, null);
        Banner saved = sampleBanner(2L);
        when(bannerRepository.save(any(Banner.class))).thenReturn(saved);

        BannerResult result = bannerService.createBanner(command);

        assertNotNull(result);
        verify(bannerRepository).save(any(Banner.class));
    }

    @Test
    @DisplayName("존재하지 않는 배너 수정 시 BannerNotFoundException")
    void updateBanner_notFound_throwsBannerNotFoundException() {
        when(bannerRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateBannerCommand command = new UpdateBannerCommand("변경", null, null, null, null, null, null);

        assertThrows(BannerNotFoundException.class, () -> bannerService.updateBanner(999L, command));
    }

    @Test
    @DisplayName("배너 수정 — null 필드는 기존 값 유지")
    void updateBanner_nullFieldsPreserveExisting() {
        Banner banner = sampleBanner(1L);
        when(bannerRepository.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenReturn(banner);

        UpdateBannerCommand command = new UpdateBannerCommand("변경된 제목", null, null, null, null, null, null);
        bannerService.updateBanner(1L, command);

        assertEquals("변경된 제목", banner.getTitle());
        assertEquals("https://cdn.fandrops.com/banner.jpg", banner.getImageUrl()); // 유지
    }

    @Test
    @DisplayName("배너 삭제 — soft delete (is_active=false)")
    void deleteBanner_setsIsActiveFalse() {
        Banner banner = sampleBanner(1L);
        when(bannerRepository.findById(1L)).thenReturn(Optional.of(banner));
        when(bannerRepository.save(any(Banner.class))).thenReturn(banner);

        bannerService.deleteBanner(1L);

        assertFalse(banner.isActive());
        verify(bannerRepository).save(banner);
    }

    @Test
    @DisplayName("존재하지 않는 배너 삭제 시 BannerNotFoundException")
    void deleteBanner_notFound_throwsBannerNotFoundException() {
        when(bannerRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(BannerNotFoundException.class, () -> bannerService.deleteBanner(999L));
    }
}