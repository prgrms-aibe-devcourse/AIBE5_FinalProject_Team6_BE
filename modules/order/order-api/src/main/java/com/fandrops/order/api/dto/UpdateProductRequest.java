package com.fandrops.order.api.dto;

import com.fandrops.order.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    /** null = 이미지 변경 없음, 빈 리스트([]) = 전체 삭제, 값 있음 = 전체 교체. 최대 5장. */
    @Size(max = 5, message = "상품 이미지는 최대 5장까지 등록할 수 있습니다.")
    private List<String> imageUrls;
}
