package com.fandrops.order.application.dto;

import com.fandrops.order.domain.ProductStatus;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class UpdateProductCommand {
    private final Long productId;
    private final String name;
    private final BigDecimal price;
    private final ProductStatus status;
    private final LocalDateTime dropsStartAt;
    private final LocalDateTime dropsEndAt;
    /** null = 이미지 변경 없음, 빈 리스트 = 전체 삭제, 값 있음 = 교체 */
    private final List<String> imageUrls;

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status) {
        this(productId, name, price, status, null, null, null);
    }

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt) {
        this(productId, name, price, status, dropsStartAt, dropsEndAt, null);
    }

    public UpdateProductCommand(Long productId, String name, BigDecimal price, ProductStatus status,
                                LocalDateTime dropsStartAt, LocalDateTime dropsEndAt,
                                List<String> imageUrls) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.dropsStartAt = dropsStartAt;
        this.dropsEndAt = dropsEndAt;
        this.imageUrls = imageUrls;
    }
}
