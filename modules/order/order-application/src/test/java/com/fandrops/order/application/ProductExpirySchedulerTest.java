package com.fandrops.order.application;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductExpiryScheduler 단위 테스트")
class ProductExpirySchedulerTest {

    @Mock private ProductRepository productRepository;

    @InjectMocks
    private ProductExpiryScheduler sut;

    private Product dropsProduct(Long id) {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now().minusMinutes(1);
        return Product.of(id, 10L, "만료드롭스" + id,
                BigDecimal.valueOf(10000), ProductStatus.ON_SALE, start, end, LocalDateTime.now());
    }

    @Nested
    @DisplayName("expireDrops()")
    class ExpireDrops {

        @Test
        @DisplayName("만료 드롭스 상품을 SOLD_OUT으로 전이하고 저장")
        void expireDrops_marksSoldOutAndSaves() {
            Product p1 = dropsProduct(1L);
            Product p2 = dropsProduct(2L);
            given(productRepository.findExpiredDrops(any(LocalDateTime.class)))
                    .willReturn(List.of(p1, p2));
            given(productRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            sut.expireDrops();

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository, times(2)).save(captor.capture());
            captor.getAllValues().forEach(p ->
                    assertEquals(ProductStatus.SOLD_OUT, p.getStatus()));
        }

        @Test
        @DisplayName("만료 상품이 없으면 save 호출 없음")
        void expireDrops_noExpired_noSave() {
            given(productRepository.findExpiredDrops(any(LocalDateTime.class)))
                    .willReturn(List.of());

            sut.expireDrops();

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("개별 상품 저장 실패 시 다른 상품 처리 계속 진행")
        void expireDrops_singleFailure_continuesOthers() {
            Product p1 = dropsProduct(1L);
            Product p2 = dropsProduct(2L);
            given(productRepository.findExpiredDrops(any(LocalDateTime.class)))
                    .willReturn(List.of(p1, p2));
            given(productRepository.save(p1)).willThrow(new RuntimeException("DB 오류"));
            given(productRepository.save(p2)).willAnswer(inv -> inv.getArgument(0));

            sut.expireDrops();

            verify(productRepository, times(2)).save(any());
            assertEquals(ProductStatus.SOLD_OUT, p2.getStatus());
        }
    }
}
