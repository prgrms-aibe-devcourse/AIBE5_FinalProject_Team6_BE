package com.fandrops.order.application;

import com.fandrops.order.domain.Product;
import com.fandrops.order.domain.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Product.update() 도메인 불변식 검증 — zombie product 방지 가드(P1).
 * 서비스 레이어 가드와 독립적으로 도메인 계층에서도 partial drops update를 차단한다.
 */
@DisplayName("Product 도메인 불변식 — drops partial update 방어")
class ProductDomainGuardTest {

    private static final Long ARTIST_ID = 1L;

    private Product regularProduct() {
        return Product.of(1L, ARTIST_ID, "상시 상품", BigDecimal.valueOf(10000),
                ProductStatus.ON_SALE, null, null, LocalDateTime.now());
    }

    private Product dropsProduct() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(2);
        return Product.of(2L, ARTIST_ID, "드롭스 상품", BigDecimal.valueOf(10000),
                ProductStatus.ON_SALE, start, end, LocalDateTime.now());
    }

    @Test
    @DisplayName("startAt만 전달 시 IllegalArgumentException — zombie product 차단")
    void update_onlyStartAt_throwsIllegalArgument() {
        Product product = regularProduct();
        assertThrows(IllegalArgumentException.class,
                () -> product.update(null, null, null, LocalDateTime.now().plusDays(1), null));
    }

    @Test
    @DisplayName("endAt만 전달 시 IllegalArgumentException — zombie product 차단")
    void update_onlyEndAt_throwsIllegalArgument() {
        Product product = regularProduct();
        assertThrows(IllegalArgumentException.class,
                () -> product.update(null, null, null, null, LocalDateTime.now().plusDays(2)));
    }

    @Test
    @DisplayName("양쪽 모두 null 전달 시 정상 처리 — 기존 drops 기간 유지")
    void update_bothNull_keepExistingDropsPeriod() {
        Product product = dropsProduct();
        LocalDateTime originalStart = product.getDropsStartAt();
        LocalDateTime originalEnd = product.getDropsEndAt();

        assertDoesNotThrow(() -> product.update("수정된 이름", null, null, null, null));

        assertEquals(originalStart, product.getDropsStartAt());
        assertEquals(originalEnd, product.getDropsEndAt());
    }

    @Test
    @DisplayName("양쪽 모두 non-null 전달 시 정상 갱신")
    void update_bothNonNull_updatesDropsPeriod() {
        Product product = dropsProduct();
        LocalDateTime newStart = LocalDateTime.now().plusDays(3);
        LocalDateTime newEnd = LocalDateTime.now().plusDays(4);

        assertDoesNotThrow(() -> product.update(null, null, null, newStart, newEnd));

        assertEquals(newStart, product.getDropsStartAt());
        assertEquals(newEnd, product.getDropsEndAt());
    }

    @Test
    @DisplayName("startAt >= endAt이면 IllegalArgumentException")
    void update_startAfterEnd_throwsIllegalArgument() {
        Product product = regularProduct();
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        assertThrows(IllegalArgumentException.class,
                () -> product.update(null, null, null, start, end));
    }
}
