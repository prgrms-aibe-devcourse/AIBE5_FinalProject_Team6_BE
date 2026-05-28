package com.fandrops.community.infrastructure.feed.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "feed_like",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_feed_like_fan",    columnNames = {"fan_id", "feed_id"}),
                @UniqueConstraint(name = "uq_feed_like_artist", columnNames = {"artist_member_id", "feed_id"})
        })
@Check(constraints = "(fan_id IS NOT NULL) != (artist_member_id IS NOT NULL)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FeedLikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", nullable = false)
    private Long feedId;

    @Column(name = "fan_id")
    private Long fanId;

    @Column(name = "artist_member_id")
    private Long artistMemberId;

    @Column(name = "artist_id")
    private Long artistId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}