package com.fandrops.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateCartItemRequest {

    @Min(1)
    @Max(99)
    private int quantity;
}
