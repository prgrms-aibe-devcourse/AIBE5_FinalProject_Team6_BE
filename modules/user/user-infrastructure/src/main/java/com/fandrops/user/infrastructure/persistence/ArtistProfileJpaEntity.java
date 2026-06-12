package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.ArtistProfile;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "artist_profile")
public class ArtistProfileJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agency_id", nullable = false)
    private Long agencyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "fan_count", nullable = false)
    private long fanCount;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column
    private String bio;

    @Column(name = "homepage_url")
    private String homepageUrl;

    @Column(name = "youtube_url")
    private String youtubeUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    protected ArtistProfileJpaEntity() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getProfileImageUrl() { return profileImageUrl; }

    public static ArtistProfileJpaEntity from(ArtistProfile domain) {
        ArtistProfileJpaEntity e = new ArtistProfileJpaEntity();
        e.id = domain.getId();
        e.agencyId = domain.getAgencyId();
        e.name = domain.getName();
        e.fanCount = domain.getFanCount();
        e.joinedAt = domain.getJoinedAt();
        e.profileImageUrl = domain.getProfileImageUrl();
        e.coverImageUrl = domain.getCoverImageUrl();
        e.bio = domain.getBio();
        e.homepageUrl = domain.getHomepageUrl();
        e.youtubeUrl = domain.getYoutubeUrl();
        e.instagramUrl = domain.getInstagramUrl();
        return e;
    }

    public ArtistProfile toDomain() {
        return ArtistProfile.reconstitute(
                id, agencyId, name, fanCount, joinedAt,
                profileImageUrl, coverImageUrl, bio,
                homepageUrl, youtubeUrl, instagramUrl);
    }
}
