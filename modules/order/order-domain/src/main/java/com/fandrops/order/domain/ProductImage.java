package com.fandrops.order.domain;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ProductImage {

    private static final int MAX_IMAGES = 5;

    private Long id;
    private Long productId;
    private String imageUrl;
    private int sortOrder;
    private boolean primary;

    private ProductImage() {}

    /** imageUrls 목록으로 ProductImage 리스트 생성. 첫 번째가 대표 이미지(isPrimary). */
    public static List<ProductImage> from(Long productId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        if (imageUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("상품 이미지는 최대 " + MAX_IMAGES + "장까지 등록할 수 있습니다.");
        }
        List<ProductImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage img = new ProductImage();
            img.productId = productId;
            img.imageUrl = imageUrls.get(i);
            img.sortOrder = i;
            img.primary = (i == 0);
            images.add(img);
        }
        return images;
    }

    public static ProductImage of(Long id, Long productId, String imageUrl, int sortOrder, boolean primary) {
        ProductImage img = new ProductImage();
        img.id = id;
        img.productId = productId;
        img.imageUrl = imageUrl;
        img.sortOrder = sortOrder;
        img.primary = primary;
        return img;
    }
}
