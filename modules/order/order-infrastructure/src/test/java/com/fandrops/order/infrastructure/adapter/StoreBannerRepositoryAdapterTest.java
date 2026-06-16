package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.StoreBanner;
import com.fandrops.order.infrastructure.persistence.StoreBannerJpaEntity;
import com.fandrops.order.infrastructure.persistence.StoreBannerJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoreBannerRepositoryAdapter 단위 테스트")
class StoreBannerRepositoryAdapterTest {

    @Mock
    private StoreBannerJpaRepository jpaRepository;

    @InjectMocks
    private StoreBannerRepositoryAdapter sut;

    private static final Long BANNER_ID = 1L;

    private StoreBanner newBanner() {
        return StoreBanner.create("테스트 배너", "https://cdn.test/img.jpg",
                "https://fandrops.test/store", 1, null, null, null);
    }

    private StoreBanner savedBanner() {
        return StoreBanner.of(BANNER_ID, "테스트 배너", "https://cdn.test/img.jpg",
                "https://fandrops.test/store", 1, true, null, null, null);
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("id 없는 신규 배너 — from() 경로로 INSERT")
        void save_newBanner_callsFrom() {
            given(jpaRepository.save(any())).willReturn(StoreBannerJpaEntity.from(savedBanner()));

            StoreBanner result = sut.save(newBanner());

            verify(jpaRepository).save(any(StoreBannerJpaEntity.class));
            assertNotNull(result);
        }

        @Test
        @DisplayName("id 있는 기존 배너 — fromWithId() 경로로 UPDATE")
        void save_existingBanner_callsFromWithId() {
            given(jpaRepository.save(any())).willReturn(StoreBannerJpaEntity.fromWithId(savedBanner()));

            StoreBanner result = sut.save(savedBanner());

            verify(jpaRepository).save(any(StoreBannerJpaEntity.class));
            assertEquals(BANNER_ID, result.getId());
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("존재하면 Optional<StoreBanner> 반환")
        void findById_found() {
            given(jpaRepository.findById(BANNER_ID))
                    .willReturn(Optional.of(StoreBannerJpaEntity.fromWithId(savedBanner())));

            Optional<StoreBanner> result = sut.findById(BANNER_ID);

            assertTrue(result.isPresent());
            assertEquals(BANNER_ID, result.get().getId());
        }

        @Test
        @DisplayName("없으면 Optional.empty() 반환")
        void findById_notFound() {
            given(jpaRepository.findById(BANNER_ID)).willReturn(Optional.empty());

            assertTrue(sut.findById(BANNER_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("findActiveStoreBanners()")
    class FindActiveStoreBanners {

        @Test
        @DisplayName("현재 시각 기준 활성 STORE 배너 목록 반환")
        void findActiveStoreBanners_returnsList() {
            given(jpaRepository.findActiveStoreBanners(any(LocalDateTime.class)))
                    .willReturn(List.of(StoreBannerJpaEntity.fromWithId(savedBanner())));

            List<StoreBanner> result = sut.findActiveStoreBanners();

            assertEquals(1, result.size());
            assertEquals(BANNER_ID, result.get(0).getId());
        }

        @Test
        @DisplayName("활성 배너 없으면 빈 목록 반환")
        void findActiveStoreBanners_empty() {
            given(jpaRepository.findActiveStoreBanners(any(LocalDateTime.class)))
                    .willReturn(List.of());

            assertTrue(sut.findActiveStoreBanners().isEmpty());
        }
    }
}
