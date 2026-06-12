package com.fandrops.order.api.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RestockRequest {

    @Min(1)
    private int quantity;
}
