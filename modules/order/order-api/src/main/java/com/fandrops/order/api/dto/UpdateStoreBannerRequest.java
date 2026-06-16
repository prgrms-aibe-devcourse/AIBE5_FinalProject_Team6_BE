package com.fandrops.order.api.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateStoreBannerRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 500)
    private String imageUrl;

    @Size(max = 500)
    private String landingUrl;

    private Integer exposureOrder;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long productId;
}
