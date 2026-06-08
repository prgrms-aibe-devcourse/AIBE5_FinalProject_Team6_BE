package com.fandrops.community.domain.follow;

import java.time.Clock;
import java.time.LocalDateTime;

public class UserFollow {

    private Long id;
    private final Long fanId;
    private final Long artistId;
    private final LocalDateTime followedAt;

    private UserFollow(Long id, Long fanId, Long artistId, LocalDateTime followedAt) {
        this.id = id;
        this.fanId = fanId;
        this.artistId = artistId;
        this.followedAt = followedAt;
    }

    public static UserFollow create(Long fanId, Long artistId, Clock clock) {
        return new UserFollow(null, fanId, artistId, LocalDateTime.now(clock));
    }

    public static UserFollow reconstruct(Long id, Long fanId, Long artistId, LocalDateTime followedAt) {
        return new UserFollow(id, fanId, artistId, followedAt);
    }

    public Long getId() { return id; }
    public Long getFanId() { return fanId; }
    public Long getArtistId() { return artistId; }
    public LocalDateTime getFollowedAt() { return followedAt; }
}