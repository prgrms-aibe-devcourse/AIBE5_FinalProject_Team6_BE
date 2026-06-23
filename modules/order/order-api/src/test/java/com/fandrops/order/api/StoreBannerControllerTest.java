package com.fandrops.order.api;

import com.fandrops.order.api.dto.CreateStoreBannerRequest;
import com.fandrops.order.api.dto.UpdateStoreBannerRequest;
import com.fandrops.order.application.StoreBannerService;
import com.fandrops.order.application.dto.CreateStoreBannerCommand;
import com.fandrops.order.application.dto.StoreBannerResponse;
import com.fandrops.order.application.dto.UpdateStoreBannerCommand;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreBannerController 단위 테스트")
class StoreBannerControllerTest {

    @Mock private StoreBannerService storeBannerService;

    @InjectMocks
    private StoreBannerController sut;

    private static final Long BANNER_ID = 1L;

    private CreateStoreBannerRequest createRequest() {
        CreateStoreBannerRequest req = mock(CreateStoreBannerRequest.class);
        given(req.getTitle()).willReturn("스토어 배너");
        given(req.getImageUrl()).willReturn("https://cdn.test/img.jpg");
        given(req.getLandingUrl()).willReturn("https://fandrops.test/store");
        given(req.getExposureOrder()).willReturn(1);
        return req;
    }

    @Nested
    @DisplayName("GET /api/v1/store-banners")
    class GetActiveStoreBanners {

        @Test
        @DisplayName("활성 배너 목록 → 200 반환")
        void returns200WithList() {
            given(storeBannerService.getActiveStoreBanners()).willReturn(List.of());

            ResponseEntity<?> response = sut.getActiveStoreBanners();

            assertEquals(200, response.getStatusCode().value());
            verify(storeBannerService).getActiveStoreBanners();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/admin/store-banners")
    class GetAllStoreBanners {

        @Test
        @DisplayName("전체 배너 목록 → 200 반환")
        void returns200WithList() {
            given(storeBannerService.getAllStoreBanners()).willReturn(List.of());

            ResponseEntity<?> response = sut.getAllStoreBanners();

            assertEquals(200, response.getStatusCode().value());
            verify(storeBannerService).getAllStoreBanners();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/admin/store-banners")
    class CreateStoreBanner {

        @Test
        @DisplayName("유효한 요청 → 201 반환 및 서비스 위임 확인")
        void returns201AndDelegatesToService() {
            given(storeBannerService.createStoreBanner(any(CreateStoreBannerCommand.class)))
                    .willReturn(BANNER_ID);

            ResponseEntity<?> response = sut.createStoreBanner(createRequest());

            assertEquals(201, response.getStatusCode().value());
            verify(storeBannerService).createStoreBanner(any(CreateStoreBannerCommand.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/admin/store-banners/{id}")
    class UpdateStoreBanner {

        @Test
        @DisplayName("수정 요청 → 200 반환 및 bannerId 포함")
        void returns200WithBannerId() {
            willDoNothing().given(storeBannerService)
                    .updateStoreBanner(any(UpdateStoreBannerCommand.class));

            ResponseEntity<?> response = sut.updateStoreBanner(BANNER_ID, new UpdateStoreBannerRequest());

            assertEquals(200, response.getStatusCode().value());
            verify(storeBannerService).updateStoreBanner(any(UpdateStoreBannerCommand.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/admin/store-banners/{id}")
    class DeleteStoreBanner {

        @Test
        @DisplayName("삭제 요청 → 204 반환")
        void returns204() {
            willDoNothing().given(storeBannerService).deleteStoreBanner(BANNER_ID);

            ResponseEntity<?> response = sut.deleteStoreBanner(BANNER_ID);

            assertEquals(204, response.getStatusCode().value());
            verify(storeBannerService).deleteStoreBanner(BANNER_ID);
        }
    }
}
