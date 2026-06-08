package com.fandrops.community.domain.follow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class UserFollowTest {

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("create — id null, 필드 정상 설정, followedAt은 clock 기준")
    void create_initialState() {
        UserFollow follow = UserFollow.create(77L, 10L, clock);

        assertNull(follow.getId());
        assertEquals(77L, follow.getFanId());
        assertEquals(10L, follow.getArtistId());
        assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0, 0), follow.getFollowedAt());
    }

    @Test
    @DisplayName("reconstruct — 전달한 id·필드가 그대로 복원됨")
    void reconstruct_restoresAllFields() {
        LocalDateTime at = LocalDateTime.of(2026, 5, 15, 12, 0, 0);
        UserFollow follow = UserFollow.reconstruct(99L, 77L, 10L, at);

        assertEquals(99L, follow.getId());
        assertEquals(77L, follow.getFanId());
        assertEquals(10L, follow.getArtistId());
        assertEquals(at, follow.getFollowedAt());
    }
}