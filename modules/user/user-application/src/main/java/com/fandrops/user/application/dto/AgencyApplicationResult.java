package com.fandrops.user.application.dto;

import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationStatus;

import java.time.LocalDateTime;

public record AgencyApplicationResult(
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
    public static AgencyApplicationResult from(AgencyApplication app) {
        return new AgencyApplicationResult(
                app.getId(),
                app.getCompanyName(),
                app.getBusinessRegistrationNumber(),
                app.getRepresentativeName(),
                app.getContactEmail(),
                app.getContactPhone(),
                app.getIntroduction(),
                app.getTargetArtistName(),
                app.getStatus(),
                app.getRejectReason(),
                app.getAppliedAt(),
                app.getReviewedAt()
        );
    }
}
