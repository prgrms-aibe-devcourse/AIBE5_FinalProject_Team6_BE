package com.fandrops.order.application;

import com.fandrops.order.domain.ProductImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductImage 도메인 불변식")
class ProductImageDomainTest {

    private static final Long PRODUCT_ID = 1L;

    @Test
    @DisplayName("5장 이하 정상 생성 — 첫 번째가 isPrimary")
    void from_validUrls_firstIsPrimary() {
        List<String> urls = List.of("https://s3/a.jpg", "https://s3/b.jpg", "https://s3/c.jpg");
        List<ProductImage> images = ProductImage.from(PRODUCT_ID, urls);

        assertEquals(3, images.size());
        assertTrue(images.get(0).isPrimary());
        assertFalse(images.get(1).isPrimary());
        assertFalse(images.get(2).isPrimary());
        assertEquals(0, images.get(0).getSortOrder());
        assertEquals(1, images.get(1).getSortOrder());
        assertEquals(2, images.get(2).getSortOrder());
    }

    @Test
    @DisplayName("6장 이상이면 IllegalArgumentException")
    void from_sixUrls_throwsIllegalArgument() {
        List<String> sixUrls = List.of("u1", "u2", "u3", "u4", "u5", "u6");
        assertThrows(IllegalArgumentException.class,
                () -> ProductImage.from(PRODUCT_ID, sixUrls));
    }

    @Test
    @DisplayName("null 전달 시 빈 리스트 반환")
    void from_null_returnsEmptyList() {
        List<ProductImage> images = ProductImage.from(PRODUCT_ID, null);
        assertTrue(images.isEmpty());
    }

    @Test
    @DisplayName("빈 리스트 전달 시 빈 리스트 반환")
    void from_emptyList_returnsEmptyList() {
        List<ProductImage> images = ProductImage.from(PRODUCT_ID, Collections.emptyList());
        assertTrue(images.isEmpty());
    }

    @Test
    @DisplayName("정확히 5장 — 경계값 정상 처리")
    void from_fiveUrls_allowed() {
        List<String> fiveUrls = List.of("u1", "u2", "u3", "u4", "u5");
        List<ProductImage> images = ProductImage.from(PRODUCT_ID, fiveUrls);
        assertEquals(5, images.size());
    }
}
