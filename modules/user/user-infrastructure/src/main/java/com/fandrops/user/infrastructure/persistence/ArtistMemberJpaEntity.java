package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.UserRole;
import jakarta.persistence.*;

@Entity
@Table(name = "artist_member")
public class ArtistMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(name = "login_id", nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    protected ArtistMemberJpaEntity() {}

    public static ArtistMemberJpaEntity from(ArtistMember domain) {
        ArtistMemberJpaEntity e = new ArtistMemberJpaEntity();
        e.id = domain.getId();
        e.artistId = domain.getArtistId();
        e.loginId = domain.getLoginId();
        e.passwordHash = domain.getPasswordHash();
        e.memberName = domain.getMemberName();
        e.role = domain.getRole();
        e.profileImageUrl = domain.getProfileImageUrl();
        return e;
    }

    public ArtistMember toDomain() {
        return ArtistMember.builder()
                .id(id)
                .artistId(artistId)
                .loginId(loginId)
                .passwordHash(passwordHash)
                .memberName(memberName)
                .role(role)
                .profileImageUrl(profileImageUrl)
                .build();
    }
}
