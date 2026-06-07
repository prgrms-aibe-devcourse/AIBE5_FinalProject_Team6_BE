package com.fandrops.user.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public class AgencyApplication {

    private final Long id;
    private final String companyName;
    private final String businessRegistrationNumber;
    private final String representativeName;
    private final String contactEmail;
    private final String contactPhone;
    private final String introduction;
    private final String targetArtistName;
    private AgencyApplicationStatus status;
    private String rejectReason;
    private final LocalDateTime appliedAt;
    private LocalDateTime reviewedAt;

    // 생성 시나리오: 항상 PENDING으로 시작
    private AgencyApplication(Builder builder) {
        this.id = builder.id;
        this.companyName = builder.companyName;
        this.businessRegistrationNumber = builder.businessRegistrationNumber;
        this.representativeName = builder.representativeName;
        this.contactEmail = builder.contactEmail;
        this.contactPhone = builder.contactPhone;
        this.introduction = builder.introduction;
        this.targetArtistName = builder.targetArtistName;
        this.status = AgencyApplicationStatus.PENDING;
        this.appliedAt = builder.appliedAt != null ? builder.appliedAt : LocalDateTime.now(ZoneOffset.UTC);
    }

    // 재구성 시나리오: Infrastructure가 DB에서 읽어올 때 사용. status를 외부에서 주입 가능
    private AgencyApplication(
            Long id, String companyName, String businessRegistrationNumber,
            String representativeName, String contactEmail, String contactPhone,
            String introduction, String targetArtistName, AgencyApplicationStatus status,
            String rejectReason, LocalDateTime appliedAt, LocalDateTime reviewedAt) {
        this.id = id;
        this.companyName = companyName;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.representativeName = representativeName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.introduction = introduction;
        this.targetArtistName = targetArtistName;
        this.status = status;
        this.rejectReason = rejectReason;
        this.appliedAt = appliedAt;
        this.reviewedAt = reviewedAt;
    }

    public static AgencyApplication reconstitute(
            Long id, String companyName, String businessRegistrationNumber,
            String representativeName, String contactEmail, String contactPhone,
            String introduction, String targetArtistName, AgencyApplicationStatus status,
            String rejectReason, LocalDateTime appliedAt, LocalDateTime reviewedAt) {
        Objects.requireNonNull(id, "id는 필수입니다");
        Objects.requireNonNull(status, "status는 필수입니다");
        // AA-2: REJECTED 상태에는 rejectReason 필수
        if (status == AgencyApplicationStatus.REJECTED
                && (rejectReason == null || rejectReason.isBlank())) {
            throw new IllegalArgumentException("REJECTED 신청서는 rejectReason이 필수입니다.");
        }
        // 심사 완료 상태에는 reviewedAt 필수
        if (status.isTerminal() && reviewedAt == null) {
            throw new IllegalArgumentException("심사 완료 신청서는 reviewedAt이 필수입니다.");
        }
        return new AgencyApplication(id, companyName, businessRegistrationNumber,
                representativeName, contactEmail, contactPhone, introduction,
                targetArtistName, status, rejectReason, appliedAt, reviewedAt);
    }

    // 불변조건 AA-3: 승인 시 AGENCY_ACCOUNT + ARTIST_PROFILE 연쇄 생성은 ApplicationService 책임
    public void approve(LocalDateTime reviewedAt) {
        validateNotTerminal();
        Objects.requireNonNull(reviewedAt, "심사 일시는 필수입니다.");
        this.status = AgencyApplicationStatus.APPROVED;
        this.reviewedAt = reviewedAt;
    }

    // 불변조건 AA-2: REJECTED이면 rejectReason NOT NULL
    public void reject(String rejectReason, LocalDateTime reviewedAt) {
        validateNotTerminal();
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        Objects.requireNonNull(reviewedAt, "심사 일시는 필수입니다.");
        this.status = AgencyApplicationStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.reviewedAt = reviewedAt;
    }

    // 불변조건 AA-1: 종료 상태에서 재전이 불가
    private void validateNotTerminal() {
        if (this.status.isTerminal()) {
            throw new IllegalStateException(
                "이미 심사가 완료된 신청서입니다. 현재 상태: " + this.status
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getBusinessRegistrationNumber() { return businessRegistrationNumber; }
    public String getRepresentativeName() { return representativeName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getIntroduction() { return introduction; }
    public String getTargetArtistName() { return targetArtistName; }
    public AgencyApplicationStatus getStatus() { return status; }
    public String getRejectReason() { return rejectReason; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }

    public static class Builder {
        private Long id;
        private String companyName;
        private String businessRegistrationNumber;
        private String representativeName;
        private String contactEmail;
        private String contactPhone;
        private String introduction;
        private String targetArtistName;
        private LocalDateTime appliedAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder companyName(String companyName) { this.companyName = companyName; return this; }
        public Builder businessRegistrationNumber(String v) { this.businessRegistrationNumber = v; return this; }
        public Builder representativeName(String v) { this.representativeName = v; return this; }
        public Builder contactEmail(String v) { this.contactEmail = v; return this; }
        public Builder contactPhone(String v) { this.contactPhone = v; return this; }
        public Builder introduction(String v) { this.introduction = v; return this; }
        public Builder targetArtistName(String v) { this.targetArtistName = v; return this; }
        public Builder appliedAt(LocalDateTime v) { this.appliedAt = v; return this; }

        public AgencyApplication build() {
            Objects.requireNonNull(companyName, "companyName은 필수입니다");
            // businessRegistrationNumber: nullable 허용 — 1인 크리에이터는 개인사업자 번호 없을 수 있음
            Objects.requireNonNull(representativeName, "representativeName은 필수입니다");
            Objects.requireNonNull(contactEmail, "contactEmail은 필수입니다");
            Objects.requireNonNull(contactPhone, "contactPhone은 필수입니다");
            Objects.requireNonNull(introduction, "introduction은 필수입니다");
            Objects.requireNonNull(targetArtistName, "targetArtistName은 필수입니다");
            return new AgencyApplication(this);
        }
    }
}