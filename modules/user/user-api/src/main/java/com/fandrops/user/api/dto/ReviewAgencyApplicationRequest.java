package com.fandrops.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReviewAgencyApplicationRequest(
        @NotBlank String status,
        String rejectReason
) {}