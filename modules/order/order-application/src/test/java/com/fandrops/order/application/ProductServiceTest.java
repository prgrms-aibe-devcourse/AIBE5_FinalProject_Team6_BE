package com.fandrops.order.application;

import com.fandrops.order.application.dto.CreateProductCommand;
import com.fandrops.order.application.dto.ProductListResponse;
import com.fandrops.order.application.dto.ProductResponse;
import com.fandrops.order.application.dto.UpdateProductCommand;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.port.InventoryCreatePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import com.fandrops.order.domain.port.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 단위 테스트")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryCreatePort inventoryCreatePort;

    @Mock
    private InventoryReadPort inventoryReadPort;

    @InjectMocks
    private ProductService sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ARTIST_ID = 10L;

    private Product product() {
        return Product.of(PRODUCT_ID, ARTIST_ID, "테스트 상품",
                BigDecimal.valueOf(10000), ProductStatus.ON_SALE, LocalDateTime.now());
    }

    private InventoryInfo inventoryInfo() {
        return new InventoryInfo(100, 0, 100);
    }

    @Nested
    @DisplayName("getProducts()")
    class GetProducts {

        @Test
        @DisplayName("상품 목록 반환 — size 미만이면 nextCursor null")
        void getProducts_lessItemsThanSize_nextCursorNull() {
            given(productRepository.findRegularProducts(null, 20))
                    .willReturn(List.of(product()));

            ProductListResponse result = sut.getProducts(null, 20);

            assertEquals(1, result.getItems().size());
            assertNull(result.getNextCursor());
        }

        @Test
        @DisplayName("size만큼 채워지면 nextCursor = 마지막 id")
        void getProducts_fullSize_nextCursorSet() {
            List<Product> products = List.of(
                    Product.of(3L, ARTIST_ID, "상품3", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, LocalDateTime.now()),
                    Product.of(2L, ARTIST_ID, "상품2", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, LocalDateTime.now()),
                    Product.of(1L, ARTIST_ID, "상품1", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, LocalDateTime.now())
            );
            given(productRepository.findRegularProducts(null, 3)).willReturn(products);

            ProductListResponse result = sut.getProducts(null, 3);

            assertEquals(3, result.getItems().size());
            assertEquals(1L, result.getNextCursor());
        }

        @Test
        @DisplayName("상품 없으면 빈 목록 반환")
        void getProducts_empty_returnsEmpty() {
            given(productRepository.findRegularProducts(null, 20)).willReturn(List.of());

            ProductListResponse result = sut.getProducts(null, 20);

            assertTrue(result.getItems().isEmpty());
            assertNull(result.getNextCursor());
        }
    }

    @Nested
    @DisplayName("getProduct()")
    class GetProduct {

        @Test
        @DisplayName("상품 상세 조회 — 재고 포함 응답")
        void getProduct_success() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));
            given(inventoryReadPort.getByProductId(PRODUCT_ID)).willReturn(inventoryInfo());

            ProductResponse result = sut.getProduct(PRODUCT_ID);

            assertEquals(PRODUCT_ID, result.getId());
            assertEquals("테스트 상품", result.getName());
            assertEquals(100, result.getTotalQty());
            assertEquals(0, result.getReservedQty());
            assertEquals(100, result.getAvailableQty());
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 ProductNotFoundException")
        void getProduct_notFound_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> sut.getProduct(PRODUCT_ID));

            verify(inventoryReadPort, never()).getByProductId(any());
        }
    }

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        @Test
        @DisplayName("상품 저장 후 INVENTORY 행 생성 — 단일 TX")
        void createProduct_savesProductAndCreatesInventory() {
            Product saved = product();
            given(productRepository.save(any())).willReturn(saved);

            Long result = sut.createProduct(
                    new CreateProductCommand(ARTIST_ID, "테스트 상품", BigDecimal.valueOf(10000), 100));

            assertEquals(PRODUCT_ID, result);
            ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(productCaptor.capture());
            assertEquals(ProductStatus.ON_SALE, productCaptor.getValue().getStatus());
            verify(inventoryCreatePort).createInventory(eq(PRODUCT_ID), eq(100));
        }
    }

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProduct {

        @Test
        @DisplayName("이름·가격·상태 변경 성공")
        void updateProduct_success() {
            Product existing = product();
            Product updated = Product.of(PRODUCT_ID, ARTIST_ID, "변경상품",
                    BigDecimal.valueOf(20000), ProductStatus.SOLD_OUT, LocalDateTime.now());
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(existing));
            given(productRepository.save(any())).willReturn(updated);

            ProductStatus result = sut.updateProduct(new UpdateProductCommand(
                    PRODUCT_ID, "변경상품", BigDecimal.valueOf(20000), ProductStatus.SOLD_OUT));

            assertEquals(ProductStatus.SOLD_OUT, result);
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 ProductNotFoundException")
        void updateProduct_notFound_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> sut.updateProduct(new UpdateProductCommand(
                            PRODUCT_ID, null, null, ProductStatus.SOLD_OUT)));

            verify(productRepository, never()).save(any());
        }
    }
}
