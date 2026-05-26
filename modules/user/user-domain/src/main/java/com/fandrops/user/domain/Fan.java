package com.fandrops.user.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class Fan {

    private final Long id;
    private final String email;
    private String nickname;
    private final AuthProvider authProvider;
    private final String providerId;
    private boolean isAllowNotification;
    private final LocalDateTime createdAt;

    private Fan(Builder builder) {
        this.id = builder.id;
        this.email = builder.email;
        this.nickname = builder.nickname;
        this.authProvider = builder.authProvider;
        this.providerId = builder.providerId;
        this.isAllowNotification = builder.isAllowNotification;
        this.createdAt = builder.createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateNotificationConsent(boolean isAllowNotification) {
        this.isAllowNotification = isAllowNotification;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public String getProviderId() { return providerId; }
    public boolean isAllowNotification() { return isAllowNotification; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public static class Builder {
        private Long id;
        private String email;
        private String nickname;
        private AuthProvider authProvider;
        private String providerId;
        private boolean isAllowNotification = true;
        private LocalDateTime createdAt;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder authProvider(AuthProvider authProvider) { this.authProvider = authProvider; return this; }
        public Builder providerId(String providerId) { this.providerId = providerId; return this; }
        public Builder isAllowNotification(boolean isAllowNotification) { this.isAllowNotification = isAllowNotification; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Fan build() {
            Objects.requireNonNull(email, "email은 필수입니다");
            Objects.requireNonNull(authProvider, "authProvider는 필수입니다");
            Objects.requireNonNull(providerId, "providerId는 필수입니다");
            Objects.requireNonNull(nickname, "nickname은 필수입니다");
            return new Fan(this);
        }
    }
}