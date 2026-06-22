package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.ProductImage;
import com.fandrops.order.infrastructure.persistence.ProductImageJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductImageJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageRepositoryAdapter 단위 테스트")
class ProductImageRepositoryAdapterTest {

    @Mock
    private ProductImageJpaRepository jpaRepository;

    @InjectMocks
    private ProductImageRepositoryAdapter sut;

    private static final Long PRODUCT_ID = 1L;

    @Nested
    @DisplayName("saveAll()")
    class SaveAll {

        @Test
        @DisplayName("이미지 목록을 JPA 엔티티로 변환해 저장")
        void saveAll_delegatesToJpa() {
            List<ProductImage> images = ProductImage.from(PRODUCT_ID,
                    List.of("https://s3/a.jpg", "https://s3/b.jpg"));

            sut.saveAll(images);

            verify(jpaRepository).saveAll(any());
        }
    }

    @Nested
    @DisplayName("deleteByProductId()")
    class DeleteByProductId {

        @Test
        @DisplayName("productId로 이미지 전체 삭제 위임")
        void deleteByProductId_delegatesToJpa() {
            sut.deleteByProductId(PRODUCT_ID);

            verify(jpaRepository).deleteByProductId(PRODUCT_ID);
        }
    }

    @Nested
    @DisplayName("findByProductId()")
    class FindByProductId {

        @Test
        @DisplayName("sort_order 오름차순 정렬 결과 반환")
        void findByProductId_returnsSorted() {
            ProductImageJpaEntity entity = ProductImageJpaEntity.from(
                    ProductImage.of(1L, PRODUCT_ID, "https://s3/a.jpg", 0, true));
            given(jpaRepository.findByProductIdOrderBySortOrderAsc(PRODUCT_ID))
                    .willReturn(List.of(entity));

            List<ProductImage> result = sut.findByProductId(PRODUCT_ID);

            assertEquals(1, result.size());
            assertEquals("https://s3/a.jpg", result.get(0).getImageUrl());
            assertTrue(result.get(0).isPrimary());
        }

        @Test
        @DisplayName("이미지 없는 상품 — 빈 리스트 반환")
        void findByProductId_empty() {
            given(jpaRepository.findByProductIdOrderBySortOrderAsc(PRODUCT_ID))
                    .willReturn(List.of());

            assertTrue(sut.findByProductId(PRODUCT_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("findThumbnailsByProductIds()")
    class FindThumbnails {

        @Test
        @DisplayName("대표 이미지만 productId → imageUrl 맵으로 반환")
        void findThumbnails_returnsPrimaryMap() {
            ProductImageJpaEntity primary = ProductImageJpaEntity.from(
                    ProductImage.of(1L, PRODUCT_ID, "https://s3/thumb.jpg", 0, true));
            given(jpaRepository.findPrimaryByProductIds(List.of(PRODUCT_ID)))
                    .willReturn(List.of(primary));

            Map<Long, String> result = sut.findThumbnailsByProductIds(List.of(PRODUCT_ID));

            assertEquals("https://s3/thumb.jpg", result.get(PRODUCT_ID));
        }

        @Test
        @DisplayName("대표 이미지 없는 경우 빈 맵 반환")
        void findThumbnails_empty() {
            given(jpaRepository.findPrimaryByProductIds(any())).willReturn(List.of());

            assertTrue(sut.findThumbnailsByProductIds(List.of(PRODUCT_ID)).isEmpty());
        }
    }
}
