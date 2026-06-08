package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AdminAccount;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "admin_account")
public class AdminAccountJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AdminAccountJpaEntity() {}

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public static AdminAccountJpaEntity from(AdminAccount domain) {
        AdminAccountJpaEntity entity = new AdminAccountJpaEntity();
        entity.id = domain.getId();
        entity.loginId = domain.getLoginId();
        entity.passwordHash = domain.getPasswordHash();
        return entity;
    }

    public AdminAccount toDomain() {
        return new AdminAccount(id, loginId, passwordHash);
    }
}
