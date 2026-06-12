package com.fandrops.user.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fandrops.user.api.jackson.OptionalLocalDateTimeDeserializer;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;
import java.util.Optional;

public record UpdateBannerRequest(
        String title,
        String imageUrl,
        String landingUrl,
        @Min(0) Integer exposureOrder,
        Boolean isActive,
        @JsonDeserialize(using = OptionalLocalDateTimeDeserializer.class) Optional<LocalDateTime> startAt,
        @JsonDeserialize(using = OptionalLocalDateTimeDeserializer.class) Optional<LocalDateTime> endAt
) {}