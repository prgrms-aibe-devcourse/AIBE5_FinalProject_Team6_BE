package com.fandrops.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CreateProductRequest {

    @NotNull
    private Long artistId;

    @NotBlank
    private String name;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal price;

    @Min(1)
    private int totalQty;

    /** null = 상시 상품, 값 있음 = 드롭스 상품 */
    private LocalDateTime dropsStartAt;
    private LocalDateTime dropsEndAt;
}
