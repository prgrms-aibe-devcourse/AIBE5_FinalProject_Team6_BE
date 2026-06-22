package com.fandrops.order.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    /** 선택. null 또는 빈 리스트 = 이미지 없음. 최대 5장, 첫 번째가 대표 이미지. */
    @Size(max = 5, message = "상품 이미지는 최대 5장까지 등록할 수 있습니다.")
    private List<String> imageUrls;
}
