package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.infrastructure.persistence.ProductJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductRepositoryAdapter 단위 테스트")
class ProductRepositoryAdapterTest {

    @Mock
    private ProductJpaRepository jpaRepository;

    @InjectMocks
    private ProductRepositoryAdapter sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ARTIST_ID = 10L;

    private Product newProduct() {
        return Product.createRegular(ARTIST_ID, "테스트 상품", BigDecimal.valueOf(10000));
    }

    private Product savedProduct() {
        return Product.of(PRODUCT_ID, ARTIST_ID, "테스트 상품",
                BigDecimal.valueOf(10000), ProductStatus.ON_SALE, null, null, LocalDateTime.now());
    }

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("id 없는 신규 상품 — from() 경로로 INSERT")
        void save_newProduct_callsFrom() {
            ProductJpaEntity entity = ProductJpaEntity.from(savedProduct());
            given(jpaRepository.save(any())).willReturn(entity);

            Product result = sut.save(newProduct());

            verify(jpaRepository).save(any(ProductJpaEntity.class));
            assertNotNull(result);
        }

        @Test
        @DisplayName("id 있는 기존 상품 — fromWithId() 경로로 UPDATE")
        void save_existingProduct_callsFromWithId() {
            ProductJpaEntity entity = ProductJpaEntity.fromWithId(savedProduct());
            given(jpaRepository.save(any())).willReturn(entity);

            Product result = sut.save(savedProduct());

            verify(jpaRepository).save(any(ProductJpaEntity.class));
            assertEquals(PRODUCT_ID, result.getId());
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("존재하면 Optional<Product> 반환")
        void findById_found() {
            given(jpaRepository.findById(PRODUCT_ID))
                    .willReturn(Optional.of(ProductJpaEntity.fromWithId(savedProduct())));

            Optional<Product> result = sut.findById(PRODUCT_ID);

            assertTrue(result.isPresent());
            assertEquals(PRODUCT_ID, result.get().getId());
        }

        @Test
        @DisplayName("없으면 Optional.empty() 반환")
        void findById_notFound() {
            given(jpaRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertTrue(sut.findById(PRODUCT_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("findRegularProducts()")
    class FindRegularProducts {

        @Test
        @DisplayName("cursor·size 조건으로 상품 목록 반환")
        void findRegularProducts_returnsList() {
            List<ProductJpaEntity> entities = List.of(ProductJpaEntity.fromWithId(savedProduct()));
            given(jpaRepository.findRegular(eq(null), any(Pageable.class))).willReturn(entities);

            List<Product> result = sut.findRegularProducts(null, 20);

            assertEquals(1, result.size());
        }
    }
}
