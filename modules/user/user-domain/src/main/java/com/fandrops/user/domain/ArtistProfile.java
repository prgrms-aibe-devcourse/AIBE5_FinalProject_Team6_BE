package com.fandrops.user.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class ArtistProfile {

    private final Long id;
    private final Long agencyId;
    private final String name;
    private long fanCount;
    private final LocalDateTime joinedAt;
    private String profileImageUrl;
    private String coverImageUrl;
    private String bio;
    private String homepageUrl;
    private String youtubeUrl;
    private String instagramUrl;

    private ArtistProfile(Builder builder) {
        this.id = builder.id;
        this.agencyId = builder.agencyId;
        this.name = builder.name;
        this.fanCount = builder.fanCount;
        this.joinedAt = builder.joinedAt != null ? builder.joinedAt : LocalDateTime.now();
        this.profileImageUrl = builder.profileImageUrl;
        this.coverImageUrl = builder.coverImageUrl;
        this.bio = builder.bio;
        this.homepageUrl = builder.homepageUrl;
        this.youtubeUrl = builder.youtubeUrl;
        this.instagramUrl = builder.instagramUrl;
    }

    public static ArtistProfile reconstitute(
            Long id, Long agencyId, String name, long fanCount, LocalDateTime joinedAt,
            String profileImageUrl, String coverImageUrl, String bio,
            String homepageUrl, String youtubeUrl, String instagramUrl) {
        Objects.requireNonNull(id, "id는 필수입니다");
        Objects.requireNonNull(agencyId, "agencyId는 필수입니다");
        Objects.requireNonNull(name, "name은 필수입니다");
        return new Builder()
                .id(id).agencyId(agencyId).name(name).fanCount(fanCount).joinedAt(joinedAt)
                .profileImageUrl(profileImageUrl).coverImageUrl(coverImageUrl).bio(bio)
                .homepageUrl(homepageUrl).youtubeUrl(youtubeUrl).instagramUrl(instagramUrl)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    public Long getId() { return id; }
    public Long getAgencyId() { return agencyId; }
    public String getName() { return name; }
    public long getFanCount() { return fanCount; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getBio() { return bio; }
    public String getHomepageUrl() { return homepageUrl; }
    public String getYoutubeUrl() { return youtubeUrl; }
    public String getInstagramUrl() { return instagramUrl; }

    public static class Builder {
        private Long id;
        private Long agencyId;
        private String name;
        private long fanCount = 0;
        private LocalDateTime joinedAt;
        private String profileImageUrl;
        private String coverImageUrl;
        private String bio;
        private String homepageUrl;
        private String youtubeUrl;
        private String instagramUrl;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder agencyId(Long agencyId) { this.agencyId = agencyId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder fanCount(long fanCount) { this.fanCount = fanCount; return this; }
        public Builder joinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; return this; }
        public Builder profileImageUrl(String v) { this.profileImageUrl = v; return this; }
        public Builder coverImageUrl(String v) { this.coverImageUrl = v; return this; }
        public Builder bio(String v) { this.bio = v; return this; }
        public Builder homepageUrl(String v) { this.homepageUrl = v; return this; }
        public Builder youtubeUrl(String v) { this.youtubeUrl = v; return this; }
        public Builder instagramUrl(String v) { this.instagramUrl = v; return this; }

        public ArtistProfile build() {
            Objects.requireNonNull(agencyId, "agencyId는 필수입니다");
            Objects.requireNonNull(name, "name은 필수입니다");
            return new ArtistProfile(this);
        }
    }
}
