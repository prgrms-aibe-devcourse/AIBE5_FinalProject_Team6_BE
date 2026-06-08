package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.UserRole;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "agency_account")
public class AgencyAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AgencyAccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "invitation_token")
    private String invitationToken;

    @Column(name = "token_expired_at")
    private LocalDateTime tokenExpiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AgencyAccountJpaEntity() {}

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public static AgencyAccountJpaEntity from(AgencyAccount domain) {
        AgencyAccountJpaEntity entity = new AgencyAccountJpaEntity();
        entity.id = domain.getId();
        entity.loginId = domain.getLoginId();
        entity.passwordHash = domain.getPasswordHash();
        entity.companyName = domain.getCompanyName();
        entity.contactEmail = domain.getContactEmail();
        entity.status = domain.getStatus();
        entity.role = domain.getRole();
        entity.invitationToken = domain.getInvitationToken();
        entity.tokenExpiredAt = domain.getTokenExpiredAt();
        entity.createdAt = domain.getCreatedAt();
        return entity;
    }

    public AgencyAccount toDomain() {
        return AgencyAccount.builder()
                .id(id)
                .loginId(loginId)
                .passwordHash(passwordHash)
                .companyName(companyName)
                .contactEmail(contactEmail)
                .status(status)
                .role(role)
                .invitationToken(invitationToken)
                .tokenExpiredAt(tokenExpiredAt)
                .createdAt(createdAt)
                .build();
    }
}
