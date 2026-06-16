package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateStoreBannerCommand;
import com.fandrops.order.application.dto.StoreBannerResponse;
import com.fandrops.order.application.dto.UpdateStoreBannerCommand;
import com.fandrops.order.domain.BannerStatus;
import com.fandrops.order.domain.StoreBanner;
import com.fandrops.order.domain.exception.StoreBannerNotFoundException;
import com.fandrops.order.domain.port.StoreBannerRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreBannerService 단위 테스트")
class StoreBannerServiceTest {

    @Mock private StoreBannerRepository storeBannerRepository;

    @InjectMocks
    private StoreBannerService sut;

    private static final Long BANNER_ID = 1L;

    private StoreBanner activeBanner() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        return StoreBanner.of(BANNER_ID, "테스트 배너", "https://img.example.com/banner.jpg",
                "https://fandrops.com/store", 1, true, start, end, null);
    }

    private StoreBanner waitingBanner() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(2);
        return StoreBanner.of(2L, "대기 배너", "https://img.example.com/banner2.jpg",
                "https://fandrops.com/store/2", 2, true, start, end, null);
    }

    @Nested
    @DisplayName("getActiveStoreBanners()")
    class GetActiveStoreBanners {

        @Test
        @DisplayName("활성 배너 목록 반환")
        void getActiveStoreBanners_returnsList() {
            given(storeBannerRepository.findActiveStoreBanners()).willReturn(List.of(activeBanner()));

            List<StoreBannerResponse> result = sut.getActiveStoreBanners();

            assertEquals(1, result.size());
            assertEquals(BANNER_ID, result.get(0).getId());
            assertEquals(BannerStatus.ACTIVE.name(), result.get(0).getStatus());
        }

        @Test
        @DisplayName("활성 배너 없으면 빈 리스트 반환")
        void getActiveStoreBanners_empty() {
            given(storeBannerRepository.findActiveStoreBanners()).willReturn(List.of());

            List<StoreBannerResponse> result = sut.getActiveStoreBanners();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("createStoreBanner()")
    class CreateStoreBanner {

        @Test
        @DisplayName("정상 생성 — 저장 후 ID 반환")
        void createStoreBanner_success() {
            given(storeBannerRepository.save(any())).willReturn(activeBanner());

            Long result = sut.createStoreBanner(new CreateStoreBannerCommand(
                    "테스트 배너", "https://img.example.com/banner.jpg",
                    "https://fandrops.com/store", 1, null, null, null));

            assertEquals(BANNER_ID, result);
            ArgumentCaptor<StoreBanner> captor = ArgumentCaptor.forClass(StoreBanner.class);
            verify(storeBannerRepository).save(captor.capture());
            assertNull(captor.getValue().getId());
            assertTrue(captor.getValue().isActive());
        }

        @Test
        @DisplayName("제목이 빈 문자열이면 IllegalArgumentException")
        void createStoreBanner_emptyTitle_throws() {
            assertThrows(IllegalArgumentException.class, () ->
                    sut.createStoreBanner(new CreateStoreBannerCommand(
                            "", "https://img.example.com/banner.jpg",
                            "https://fandrops.com/store", 1, null, null, null)));
            verify(storeBannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("startAt >= endAt 이면 IllegalArgumentException")
        void createStoreBanner_startAfterEnd_throws() {
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            assertThrows(IllegalArgumentException.class, () ->
                    sut.createStoreBanner(new CreateStoreBannerCommand(
                            "배너", "https://img.example.com/banner.jpg",
                            "https://fandrops.com/store", 1, start, end, null)));
            verify(storeBannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateStoreBanner()")
    class UpdateStoreBanner {

        @Test
        @DisplayName("제목 변경 성공")
        void updateStoreBanner_titleChanged() {
            given(storeBannerRepository.findById(BANNER_ID)).willReturn(Optional.of(activeBanner()));
            given(storeBannerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.updateStoreBanner(new UpdateStoreBannerCommand(BANNER_ID, "변경된 배너", null, null, null, null, null, null));

            ArgumentCaptor<StoreBanner> captor = ArgumentCaptor.forClass(StoreBanner.class);
            verify(storeBannerRepository).save(captor.capture());
            assertEquals("변경된 배너", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("존재하지 않는 배너 수정 시 StoreBannerNotFoundException")
        void updateStoreBanner_notFound_throws() {
            given(storeBannerRepository.findById(BANNER_ID)).willReturn(Optional.empty());

            assertThrows(StoreBannerNotFoundException.class, () ->
                    sut.updateStoreBanner(new UpdateStoreBannerCommand(
                            BANNER_ID, "변경", null, null, null, null, null, null)));
            verify(storeBannerRepository, never()).save(any());
        }

        @Test
        @DisplayName("startAt >= endAt 으로 수정 시 IllegalArgumentException")
        void updateStoreBanner_invalidPeriod_throws() {
            given(storeBannerRepository.findById(BANNER_ID)).willReturn(Optional.of(activeBanner()));
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            assertThrows(IllegalArgumentException.class, () ->
                    sut.updateStoreBanner(new UpdateStoreBannerCommand(
                            BANNER_ID, null, null, null, null, start, end, null)));
            verify(storeBannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteStoreBanner()")
    class DeleteStoreBanner {

        @Test
        @DisplayName("soft delete — is_active=false 로 저장")
        void deleteStoreBanner_deactivates() {
            given(storeBannerRepository.findById(BANNER_ID)).willReturn(Optional.of(activeBanner()));
            given(storeBannerRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.deleteStoreBanner(BANNER_ID);

            ArgumentCaptor<StoreBanner> captor = ArgumentCaptor.forClass(StoreBanner.class);
            verify(storeBannerRepository).save(captor.capture());
            assertFalse(captor.getValue().isActive());
        }

        @Test
        @DisplayName("존재하지 않는 배너 삭제 시 StoreBannerNotFoundException")
        void deleteStoreBanner_notFound_throws() {
            given(storeBannerRepository.findById(BANNER_ID)).willReturn(Optional.empty());

            assertThrows(StoreBannerNotFoundException.class,
                    () -> sut.deleteStoreBanner(BANNER_ID));
            verify(storeBannerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("StoreBanner.computeStatus()")
    class ComputeStatus {

        @Test
        @DisplayName("현재 시각이 [startAt, endAt] 범위 — ACTIVE")
        void computeStatus_active() {
            assertEquals(BannerStatus.ACTIVE, activeBanner().computeStatus(LocalDateTime.now()));
        }

        @Test
        @DisplayName("startAt이 미래 — WAITING")
        void computeStatus_waiting() {
            assertEquals(BannerStatus.WAITING, waitingBanner().computeStatus(LocalDateTime.now()));
        }

        @Test
        @DisplayName("isActive=false — INACTIVE")
        void computeStatus_inactive() {
            StoreBanner inactive = StoreBanner.of(3L, "비활성", "https://img.example.com/b.jpg",
                    "https://fandrops.com/store/3", 3, false, null, null, null);
            assertEquals(BannerStatus.INACTIVE, inactive.computeStatus(LocalDateTime.now()));
        }

        @Test
        @DisplayName("endAt이 과거 — INACTIVE")
        void computeStatus_expired() {
            StoreBanner expired = StoreBanner.of(4L, "만료", "https://img.example.com/c.jpg",
                    "https://fandrops.com/store/4", 4, true,
                    LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(1), null);
            assertEquals(BannerStatus.INACTIVE, expired.computeStatus(LocalDateTime.now()));
        }
    }
}
