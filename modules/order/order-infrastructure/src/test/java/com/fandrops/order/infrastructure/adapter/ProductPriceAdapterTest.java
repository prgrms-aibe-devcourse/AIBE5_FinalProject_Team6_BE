package com.fandrops.order.infrastructure.adapter;

import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.infrastructure.persistence.ProductJpaEntity;
import com.fandrops.order.infrastructure.persistence.ProductJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fandrops.order.domain.Product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductPriceAdapter 단위 테스트")
class ProductPriceAdapterTest {

    @Mock
    private ProductJpaRepository jpaRepository;

    @InjectMocks
    private ProductPriceAdapter sut;

    private static final Long PRODUCT_ID = 1L;
    private static final BigDecimal PRICE = BigDecimal.valueOf(15000);

    private ProductJpaEntity entity() {
        return ProductJpaEntity.fromWithId(
                Product.of(PRODUCT_ID, 10L, "상품", PRICE, ProductStatus.ON_SALE, null, null, LocalDateTime.now()));
    }

    @Nested
    @DisplayName("getPrice()")
    class GetPrice {

        @Test
        @DisplayName("상품 존재 시 가격 반환")
        void getPrice_found() {
            given(jpaRepository.findById(PRODUCT_ID)).willReturn(Optional.of(entity()));

            assertEquals(PRICE, sut.getPrice(PRODUCT_ID));
        }

        @Test
        @DisplayName("상품 없으면 ProductNotFoundException")
        void getPrice_notFound() {
            given(jpaRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> sut.getPrice(PRODUCT_ID));
        }
    }

    @Nested
    @DisplayName("getPrices()")
    class GetPrices {

        @Test
        @DisplayName("복수 상품 가격 Map 반환")
        void getPrices_returnsMap() {
            given(jpaRepository.findAllById(List.of(PRODUCT_ID))).willReturn(List.of(entity()));

            Map<Long, BigDecimal> result = sut.getPrices(List.of(PRODUCT_ID));

            assertEquals(1, result.size());
            assertEquals(PRICE, result.get(PRODUCT_ID));
        }

        @Test
        @DisplayName("빈 목록 요청 시 빈 Map 반환")
        void getPrices_emptyIds_returnsEmpty() {
            given(jpaRepository.findAllById(List.of())).willReturn(List.of());

            assertTrue(sut.getPrices(List.of()).isEmpty());
        }
    }
}
