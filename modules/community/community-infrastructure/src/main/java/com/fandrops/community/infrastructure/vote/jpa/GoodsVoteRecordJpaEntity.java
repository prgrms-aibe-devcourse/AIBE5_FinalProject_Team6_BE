package com.fandrops.community.infrastructure.vote.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "goods_vote_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_vote_fan", columnNames = {"vote_id", "fan_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GoodsVoteRecordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vote_id", nullable = false)
    private Long voteId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "fan_id", nullable = false)
    private Long fanId;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;
}
