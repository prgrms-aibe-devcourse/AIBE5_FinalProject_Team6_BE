package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "fan", uniqueConstraints = {
        @UniqueConstraint(name = "uq_fan_auth_provider_id", columnNames = {"auth_provider", "provider_id"})
})
public class FanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_allow_notification", nullable = false)
    private boolean allowNotification = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected FanJpaEntity() {}

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public static FanJpaEntity from(Fan fan) {
        FanJpaEntity entity = new FanJpaEntity();
        entity.id = fan.getId();
        entity.email = fan.getEmail();
        entity.nickname = fan.getNickname();
        entity.authProvider = fan.getAuthProvider();
        entity.providerId = fan.getProviderId();
        entity.passwordHash = fan.getPasswordHash();
        entity.allowNotification = fan.isAllowNotification();
        entity.createdAt = fan.getCreatedAt();
        return entity;
    }

    public Fan toDomain() {
        return Fan.builder()
                .id(id)
                .email(email)
                .nickname(nickname)
                .authProvider(authProvider)
                .providerId(providerId)
                .passwordHash(passwordHash)
                .allowNotification(allowNotification)
                .createdAt(createdAt)
                .build();
    }
}
