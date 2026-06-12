package com.fandrops.order.api.dto;

import com.fandrops.order.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class UpdateProductRequest {

    private String name;

    @DecimalMin("0.01")
    private BigDecimal price;

    private ProductStatus status;

    /** 드롭스 기간 수정 (null = 변경 없음) */
    private LocalDateTime dropsStartAt;
    private LocalDateTime dropsEndAt;
}
