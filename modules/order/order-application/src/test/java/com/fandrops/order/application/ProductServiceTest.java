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

    @Mock private ProductRepository productRepository;
    @Mock private InventoryCreatePort inventoryCreatePort;
    @Mock private InventoryReadPort inventoryReadPort;

    @InjectMocks
    private ProductService sut;

    private static final Long PRODUCT_ID = 1L;
    private static final Long ARTIST_ID = 10L;

    private Product product() {
        return Product.of(PRODUCT_ID, ARTIST_ID, "테스트 상품",
                BigDecimal.valueOf(10000), ProductStatus.ON_SALE, null, null, LocalDateTime.now());
    }

    private Product dropsProduct(Long id) {
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        return Product.of(id, ARTIST_ID, "드롭스상품" + id,
                BigDecimal.valueOf(10000), ProductStatus.ON_SALE, start, end, LocalDateTime.now());
    }

    private InventoryInfo inventoryInfo() {
        return new InventoryInfo(100, 0, 100);
    }

    @Nested
    @DisplayName("getProducts() — 상시")
    class GetRegularProducts {

        @Test
        @DisplayName("상시 목록 반환 — size 미만이면 nextCursor null")
        void getProducts_regular_nextCursorNull() {
            given(productRepository.findRegularProducts(null, 20)).willReturn(List.of(product()));

            ProductListResponse result = sut.getProducts("regular", null, 20);

            assertEquals(1, result.getItems().size());
            assertNull(result.getNextCursor());
        }

        @Test
        @DisplayName("size만큼 채워지면 nextCursor = 마지막 id")
        void getProducts_regular_nextCursorSet() {
            List<Product> products = List.of(
                    Product.of(3L, ARTIST_ID, "상품3", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, null, null, LocalDateTime.now()),
                    Product.of(2L, ARTIST_ID, "상품2", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, null, null, LocalDateTime.now()),
                    Product.of(1L, ARTIST_ID, "상품1", BigDecimal.valueOf(10000), ProductStatus.ON_SALE, null, null, LocalDateTime.now())
            );
            given(productRepository.findRegularProducts(null, 3)).willReturn(products);

            ProductListResponse result = sut.getProducts("regular", null, 3);

            assertEquals(3, result.getItems().size());
            assertEquals(1L, result.getNextCursor());
        }
    }

    @Nested
    @DisplayName("getProducts() — 드롭스")
    class GetDropsProducts {

        @Test
        @DisplayName("drops type 요청 시 findDropsProducts 호출")
        void getProducts_drops_callsDropsRepository() {
            given(productRepository.findDropsProducts(null, 20)).willReturn(List.of(dropsProduct(1L)));

            ProductListResponse result = sut.getProducts("drops", null, 20);

            verify(productRepository).findDropsProducts(null, 20);
            verify(productRepository, never()).findRegularProducts(any(), any(Integer.class));
            assertEquals(1, result.getItems().size());
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
            assertEquals(100, result.getTotalQty());
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
        @DisplayName("상시 상품 저장 + INVENTORY 생성")
        void createProduct_regular_savesAndCreatesInventory() {
            given(productRepository.save(any())).willReturn(product());

            Long result = sut.createProduct(
                    new CreateProductCommand(ARTIST_ID, "테스트 상품", BigDecimal.valueOf(10000), 100));

            assertEquals(PRODUCT_ID, result);
            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertFalse(captor.getValue().isDrops());
            verify(inventoryCreatePort).createInventory(eq(PRODUCT_ID), eq(100));
        }

        @Test
        @DisplayName("드롭스 상품 저장 — dropsStartAt/EndAt 설정")
        void createProduct_drops_setsDropsFields() {
            LocalDateTime start = LocalDateTime.now().plusDays(1);
            LocalDateTime end = LocalDateTime.now().plusDays(2);
            given(productRepository.save(any())).willReturn(dropsProduct(PRODUCT_ID));

            sut.createProduct(new CreateProductCommand(
                    ARTIST_ID, "드롭스", BigDecimal.valueOf(10000), 50, start, end));

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertTrue(captor.getValue().isDrops());
        }

        @Test
        @DisplayName("dropsStartAt만 입력 시 IllegalArgumentException")
        void createProduct_onlyStartAt_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.createProduct(new CreateProductCommand(
                            ARTIST_ID, "드롭스", BigDecimal.valueOf(10000), 50,
                            LocalDateTime.now().plusDays(1), null)));
        }

        @Test
        @DisplayName("dropsStartAt >= dropsEndAt 이면 IllegalArgumentException")
        void createProduct_startAfterEnd_throws() {
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            LocalDateTime end = LocalDateTime.now().plusDays(1);

            assertThrows(IllegalArgumentException.class,
                    () -> sut.createProduct(new CreateProductCommand(
                            ARTIST_ID, "드롭스", BigDecimal.valueOf(10000), 50, start, end)));
        }
    }

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProduct {

        @Test
        @DisplayName("상태 변경 성공")
        void updateProduct_success() {
            Product existing = product();
            Product updated = Product.of(PRODUCT_ID, ARTIST_ID, "변경",
                    BigDecimal.valueOf(20000), ProductStatus.SOLD_OUT, null, null, LocalDateTime.now());
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(existing));
            given(productRepository.save(any())).willReturn(updated);

            ProductStatus result = sut.updateProduct(new UpdateProductCommand(
                    PRODUCT_ID, "변경", BigDecimal.valueOf(20000), ProductStatus.SOLD_OUT));

            assertEquals(ProductStatus.SOLD_OUT, result);
        }

        @Test
        @DisplayName("존재하지 않으면 ProductNotFoundException")
        void updateProduct_notFound_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class,
                    () -> sut.updateProduct(new UpdateProductCommand(
                            PRODUCT_ID, null, null, ProductStatus.SOLD_OUT)));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("드롭스 기간 역순 수정 시 IllegalArgumentException")
        void updateProduct_invalidDropsPeriod_throws() {
            LocalDateTime start = LocalDateTime.now().plusDays(2);
            LocalDateTime end = LocalDateTime.now().plusDays(1);
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product()));

            assertThrows(IllegalArgumentException.class,
                    () -> sut.updateProduct(new UpdateProductCommand(
                            PRODUCT_ID, null, null, null, start, end)));
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("dropsStartAt만 전달 시 IllegalArgumentException — zombie product 방지 (P1)")
        void updateProduct_onlyStartAt_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.updateProduct(new UpdateProductCommand(
                            PRODUCT_ID, null, null, null, LocalDateTime.now().plusDays(1), null)));
            verify(productRepository, never()).findById(any());
            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("dropsEndAt만 전달 시 IllegalArgumentException — zombie product 방지 (P1)")
        void updateProduct_onlyEndAt_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> sut.updateProduct(new UpdateProductCommand(
                            PRODUCT_ID, null, null, null, null, LocalDateTime.now().plusDays(2))));
            verify(productRepository, never()).findById(any());
            verify(productRepository, never()).save(any());
        }
    }
}
