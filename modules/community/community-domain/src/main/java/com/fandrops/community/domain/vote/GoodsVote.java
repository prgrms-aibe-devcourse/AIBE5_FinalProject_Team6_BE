package com.fandrops.community.domain.vote;

import com.fandrops.community.domain.vote.exception.GoodsVoteDomainException;

import java.time.LocalDateTime;

public class GoodsVote {

    private final Long id;
    private final Long artistId;
    private final String title;
    private final LocalDateTime endsAt;
    private final boolean active;
    private final LocalDateTime createdAt;

    private GoodsVote(Long id, Long artistId, String title,
                      LocalDateTime endsAt, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.artistId = artistId;
        this.title = title;
        this.endsAt = endsAt;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static GoodsVote create(Long artistId, String title,
                                   LocalDateTime endsAt, LocalDateTime now) {
        if (artistId == null) {
            throw new GoodsVoteDomainException("artistId는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new GoodsVoteDomainException("title은 1~255자여야 합니다.");
        }
        if (endsAt == null || !endsAt.isAfter(now)) {
            throw new GoodsVoteDomainException("endsAt은 현재 시각 이후여야 합니다.");
        }
        return new GoodsVote(null, artistId, title, endsAt, true, now);
    }

    public static GoodsVote reconstruct(Long id, Long artistId, String title,
                                        LocalDateTime endsAt, boolean active, LocalDateTime createdAt) {
        return new GoodsVote(id, artistId, title, endsAt, active, createdAt);
    }

    public boolean isVotable(LocalDateTime now) {
        return active && endsAt.isAfter(now);
    }

    public GoodsVote close() {
        return new GoodsVote(id, artistId, title, endsAt, false, createdAt);
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public String getTitle() { return title; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
