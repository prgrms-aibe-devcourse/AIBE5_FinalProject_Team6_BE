package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AgencyApplication;
import com.fandrops.user.domain.AgencyApplicationStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "agency_application")
public class AgencyApplicationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    // nullable 허용 — 1인 크리에이터는 개인사업자 없을 수 있음, Admin 예외 심사 처리
    @Column(name = "business_registration_number")
    private String businessRegistrationNumber;

    @Column(name = "representative_name", nullable = false)
    private String representativeName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "target_artist_name", nullable = false)
    private String targetArtistName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgencyApplicationStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected AgencyApplicationJpaEntity() {}

    @PrePersist
    private void prePersist() {
        if (appliedAt == null) {
            appliedAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public static AgencyApplicationJpaEntity from(AgencyApplication domain) {
        AgencyApplicationJpaEntity entity = new AgencyApplicationJpaEntity();
        entity.id = domain.getId();
        entity.companyName = domain.getCompanyName();
        entity.businessRegistrationNumber = domain.getBusinessRegistrationNumber();
        entity.representativeName = domain.getRepresentativeName();
        entity.contactEmail = domain.getContactEmail();
        entity.contactPhone = domain.getContactPhone();
        entity.introduction = domain.getIntroduction();
        entity.targetArtistName = domain.getTargetArtistName();
        entity.status = domain.getStatus();
        entity.rejectReason = domain.getRejectReason();
        entity.appliedAt = domain.getAppliedAt();
        entity.reviewedAt = domain.getReviewedAt();
        return entity;
    }

    public AgencyApplication toDomain() {
        return AgencyApplication.reconstitute(
                id, companyName, businessRegistrationNumber,
                representativeName, contactEmail, contactPhone,
                introduction, targetArtistName, status,
                rejectReason, appliedAt, reviewedAt
        );
    }
}
