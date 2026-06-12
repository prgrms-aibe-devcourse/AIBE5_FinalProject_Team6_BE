package com.fandrops.community.infrastructure.feed.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_like",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_comment_like_fan", columnNames = {"fan_id", "comment_id"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CommentLikeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}