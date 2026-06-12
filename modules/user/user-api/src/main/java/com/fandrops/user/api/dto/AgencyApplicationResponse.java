package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.AgencyApplicationResult;
import com.fandrops.user.domain.AgencyApplicationStatus;

import java.time.LocalDateTime;

public record AgencyApplicationResponse(
        Long id,
        String companyName,
        String businessRegistrationNumber,
        String representativeName,
        String contactEmail,
        String contactPhone,
        String introduction,
        String targetArtistName,
        AgencyApplicationStatus status,
        String rejectReason,
        LocalDateTime appliedAt,
        LocalDateTime reviewedAt
) {
    public static AgencyApplicationResponse from(AgencyApplicationResult result) {
        return new AgencyApplicationResponse(
                result.id(),
                result.companyName(),
                result.businessRegistrationNumber(),
                result.representativeName(),
                result.contactEmail(),
                result.contactPhone(),
                result.introduction(),
                result.targetArtistName(),
                result.status(),
                result.rejectReason(),
                result.appliedAt(),
                result.reviewedAt()
        );
    }
}