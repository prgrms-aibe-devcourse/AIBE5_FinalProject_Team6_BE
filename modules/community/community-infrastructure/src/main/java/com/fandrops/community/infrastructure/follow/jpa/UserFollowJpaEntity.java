package com.fandrops.community.infrastructure.follow.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_follow",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_follow", columnNames = {"fan_id", "artist_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserFollowJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "artist_id", nullable = false)
    private Long artistId;

    @Column(name = "followed_at", nullable = false)
    private LocalDateTime followedAt;
}