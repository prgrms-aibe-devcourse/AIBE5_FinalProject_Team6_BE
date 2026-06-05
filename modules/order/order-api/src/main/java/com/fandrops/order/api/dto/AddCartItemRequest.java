package com.fandrops.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddCartItemRequest {

    @NotNull
    private Long productId;

    @Min(1)
    private int quantity;
}
