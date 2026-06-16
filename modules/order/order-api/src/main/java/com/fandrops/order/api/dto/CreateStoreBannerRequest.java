package com.fandrops.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateStoreBannerRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 500)
    private String imageUrl;

    @NotBlank
    @Size(max = 500)
    private String landingUrl;

    private int exposureOrder;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long productId;
}
