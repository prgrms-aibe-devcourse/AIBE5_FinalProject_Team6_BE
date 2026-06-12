package com.fandrops.user.domain;

import java.util.Objects;

public class ArtistMember {

    private final Long id;
    private final Long artistId;
    private final String loginId;
    private final String passwordHash;
    private final String memberName;
    private final UserRole role;
    private final String profileImageUrl;

    private ArtistMember(Builder builder) {
        this.id = builder.id;
        this.artistId = Objects.requireNonNull(builder.artistId, "artistId는 필수입니다");
        this.loginId = Objects.requireNonNull(builder.loginId, "loginId는 필수입니다");
        this.passwordHash = Objects.requireNonNull(builder.passwordHash, "passwordHash는 필수입니다");
        this.memberName = Objects.requireNonNull(builder.memberName, "memberName은 필수입니다");
        this.role = builder.role != null ? builder.role : UserRole.ARTIST;
        this.profileImageUrl = builder.profileImageUrl;
    }

    public static Builder builder() { return new Builder(); }

    public ArtistMember withProfileImageUrl(String url) {
        return ArtistMember.builder()
                .id(this.id).artistId(this.artistId).loginId(this.loginId)
                .passwordHash(this.passwordHash).memberName(this.memberName)
                .role(this.role).profileImageUrl(url).build();
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getMemberName() { return memberName; }
    public UserRole getRole() { return role; }
    public String getProfileImageUrl() { return profileImageUrl; }

    public static class Builder {
        private Long id;
        private Long artistId;
        private String loginId;
        private String passwordHash;
        private String memberName;
        private UserRole role;
        private String profileImageUrl;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder artistId(Long v) { this.artistId = v; return this; }
        public Builder loginId(String v) { this.loginId = v; return this; }
        public Builder passwordHash(String v) { this.passwordHash = v; return this; }
        public Builder memberName(String v) { this.memberName = v; return this; }
        public Builder role(UserRole v) { this.role = v; return this; }
        public Builder profileImageUrl(String v) { this.profileImageUrl = v; return this; }

        public ArtistMember build() { return new ArtistMember(this); }
    }
}
