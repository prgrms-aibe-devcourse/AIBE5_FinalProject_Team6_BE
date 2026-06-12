package com.fandrops.order.application;

import com.fandrops.order.application.event.RestockAlertEvent;
import com.fandrops.order.domain.InventoryInfo;
import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import com.fandrops.order.domain.RestockAlert;
import com.fandrops.order.domain.RestockAlertStatus;
import com.fandrops.order.domain.exception.ProductNotFoundException;
import com.fandrops.order.domain.exception.RestockAlertNotFoundException;
import com.fandrops.order.domain.port.InventoryIncreasePort;
import com.fandrops.order.domain.port.InventoryReadPort;
import com.fandrops.order.domain.port.ProductRepository;
import com.fandrops.order.domain.port.RestockAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestockAlertService 단위 테스트")
class RestockAlertServiceTest {

    @Mock private RestockAlertRepository restockAlertRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryIncreasePort inventoryIncreasePort;
    @Mock private InventoryReadPort inventoryReadPort;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RestockAlertService sut;

    private static final Long FAN_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long ALERT_ID = 100L;

    private Product soldOutProduct() {
        return Product.of(PRODUCT_ID, 5L, "상품", BigDecimal.valueOf(10000),
                ProductStatus.SOLD_OUT, null, null, LocalDateTime.now());
    }

    private Product onSaleProduct() {
        return Product.of(PRODUCT_ID, 5L, "상품", BigDecimal.valueOf(10000),
                ProductStatus.ON_SALE, null, null, LocalDateTime.now());
    }

    private RestockAlert pendingAlert() {
        return RestockAlert.of(ALERT_ID, FAN_ID, PRODUCT_ID, RestockAlertStatus.PENDING, LocalDateTime.now());
    }

    @Nested
    @DisplayName("subscribe()")
    class Subscribe {

        @Test
        @DisplayName("신규 구독 — PENDING 알림 생성 후 alertId 반환")
        void subscribe_new_createsPending() {
            RestockAlert saved = pendingAlert();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(onSaleProduct()));
            given(restockAlertRepository.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID))
                    .willReturn(Optional.empty());
            given(restockAlertRepository.save(any())).willReturn(saved);

            Long result = sut.subscribe(FAN_ID, PRODUCT_ID);

            assertEquals(ALERT_ID, result);
        }

        @Test
        @DisplayName("이미 PENDING이면 기존 alertId 반환 — 멱등")
        void subscribe_alreadyPending_returnsExisting() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(onSaleProduct()));
            given(restockAlertRepository.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID))
                    .willReturn(Optional.of(pendingAlert()));

            Long result = sut.subscribe(FAN_ID, PRODUCT_ID);

            assertEquals(ALERT_ID, result);
            verify(restockAlertRepository, never()).save(any());
        }

        @Test
        @DisplayName("상품 없으면 ProductNotFoundException")
        void subscribe_productNotFound_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> sut.subscribe(FAN_ID, PRODUCT_ID));
        }
    }

    @Nested
    @DisplayName("unsubscribe()")
    class Unsubscribe {

        @Test
        @DisplayName("PENDING → CANCELLED 전이 성공")
        void unsubscribe_success() {
            RestockAlert alert = pendingAlert();
            given(restockAlertRepository.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID))
                    .willReturn(Optional.of(alert));
            given(restockAlertRepository.save(any())).willReturn(alert);

            sut.unsubscribe(FAN_ID, PRODUCT_ID);

            ArgumentCaptor<RestockAlert> captor = ArgumentCaptor.forClass(RestockAlert.class);
            verify(restockAlertRepository).save(captor.capture());
            assertEquals(RestockAlertStatus.CANCELLED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("PENDING 없으면 RestockAlertNotFoundException")
        void unsubscribe_notFound_throws() {
            given(restockAlertRepository.findPendingByFanIdAndProductId(FAN_ID, PRODUCT_ID))
                    .willReturn(Optional.empty());

            assertThrows(RestockAlertNotFoundException.class,
                    () -> sut.unsubscribe(FAN_ID, PRODUCT_ID));
        }
    }

    @Nested
    @DisplayName("restock()")
    class Restock {

        @Test
        @DisplayName("SOLD_OUT 상품 재입고 시 ON_SALE 전이 + 구독자 이벤트 발행")
        void restock_soldOut_markOnSaleAndPublishEvents() {
            RestockAlert alert = pendingAlert();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(soldOutProduct()));
            given(restockAlertRepository.findAllPendingByProductId(PRODUCT_ID)).willReturn(List.of(alert));
            given(restockAlertRepository.save(any())).willReturn(alert);
            given(inventoryReadPort.getByProductId(PRODUCT_ID)).willReturn(new InventoryInfo(150, 0, 150));

            int totalQty = sut.restock(PRODUCT_ID, 50);

            assertEquals(150, totalQty);
            verify(inventoryIncreasePort).increase(PRODUCT_ID, 50);
            verify(productRepository).save(any());
            ArgumentCaptor<RestockAlertEvent> eventCaptor = ArgumentCaptor.forClass(RestockAlertEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(FAN_ID, eventCaptor.getValue().getFanId());
            assertEquals(PRODUCT_ID, eventCaptor.getValue().getProductId());
        }

        @Test
        @DisplayName("ON_SALE 상품 재입고 시 상태 변경 없음")
        void restock_onSale_noStatusChange() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(onSaleProduct()));
            given(restockAlertRepository.findAllPendingByProductId(PRODUCT_ID)).willReturn(List.of());
            given(inventoryReadPort.getByProductId(PRODUCT_ID)).willReturn(new InventoryInfo(120, 0, 120));

            sut.restock(PRODUCT_ID, 20);

            verify(productRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("상품 없으면 ProductNotFoundException")
        void restock_productNotFound_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThrows(ProductNotFoundException.class, () -> sut.restock(PRODUCT_ID, 10));
        }
    }
}
