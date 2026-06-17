package com.fandrops.user.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public class AgencyAccount {

    private final Long id;
    private final String loginId;
    private final String passwordHash;
    private final String companyName;
    private final String contactEmail;
    private final AgencyAccountStatus status;
    private final UserRole role;
    private final String invitationToken;
    private final LocalDateTime tokenExpiredAt;
    private final LocalDateTime createdAt;

    private AgencyAccount(Builder builder) {
        this.id = builder.id;
        this.loginId = Objects.requireNonNull(builder.loginId, "loginId는 필수입니다");
        this.passwordHash = Objects.requireNonNull(builder.passwordHash, "passwordHash는 필수입니다");
        this.companyName = Objects.requireNonNull(builder.companyName, "companyName은 필수입니다");
        this.contactEmail = Objects.requireNonNull(builder.contactEmail, "contactEmail은 필수입니다");
        this.status = builder.status != null ? builder.status : AgencyAccountStatus.ACTIVE;
        this.role = builder.role != null ? builder.role : UserRole.AGENCY;
        this.invitationToken = builder.invitationToken;
        this.tokenExpiredAt = builder.tokenExpiredAt;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now(ZoneOffset.UTC);
    }

    public static Builder builder() { return new Builder(); }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getCompanyName() { return companyName; }
    public String getContactEmail() { return contactEmail; }
    public AgencyAccountStatus getStatus() { return status; }
    public UserRole getRole() { return role; }
    public String getInvitationToken() { return invitationToken; }
    public LocalDateTime getTokenExpiredAt() { return tokenExpiredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public AgencyAccount withPasswordHash(String newPasswordHash) {
        return AgencyAccount.builder()
                .id(this.id).loginId(this.loginId).passwordHash(newPasswordHash)
                .companyName(this.companyName).contactEmail(this.contactEmail)
                .status(this.status).role(this.role)
                .invitationToken(this.invitationToken).tokenExpiredAt(this.tokenExpiredAt)
                .createdAt(this.createdAt).build();
    }

    public static class Builder {
        private Long id;
        private String loginId;
        private String passwordHash;
        private String companyName;
        private String contactEmail;
        private AgencyAccountStatus status;
        private UserRole role;
        private String invitationToken;
        private LocalDateTime tokenExpiredAt;
        private LocalDateTime createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder loginId(String v) { this.loginId = v; return this; }
        public Builder passwordHash(String v) { this.passwordHash = v; return this; }
        public Builder companyName(String v) { this.companyName = v; return this; }
        public Builder contactEmail(String v) { this.contactEmail = v; return this; }
        public Builder status(AgencyAccountStatus v) { this.status = v; return this; }
        public Builder role(UserRole v) { this.role = v; return this; }
        public Builder invitationToken(String v) { this.invitationToken = v; return this; }
        public Builder tokenExpiredAt(LocalDateTime v) { this.tokenExpiredAt = v; return this; }
        public Builder createdAt(LocalDateTime v) { this.createdAt = v; return this; }

        public AgencyAccount build() { return new AgencyAccount(this); }
    }
}
