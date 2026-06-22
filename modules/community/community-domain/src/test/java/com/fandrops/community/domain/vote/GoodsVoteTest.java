package com.fandrops.community.domain.vote;

import com.fandrops.community.domain.vote.exception.GoodsVoteDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GoodsVoteTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);
    private static final LocalDateTime FUTURE = NOW.plusDays(7);
    private static final LocalDateTime PAST = NOW.minusDays(1);

    @Test
    @DisplayName("유효한 인자 — 정상 생성, isActive=true")
    void create_success() {
        GoodsVote vote = GoodsVote.create(1L, "굿즈 투표", FUTURE, NOW);
        assertEquals(1L, vote.getArtistId());
        assertEquals("굿즈 투표", vote.getTitle());
        assertEquals(FUTURE, vote.getEndsAt());
        assertTrue(vote.isActive());
        assertNull(vote.getId());
    }

    @Test
    @DisplayName("artistId null → GoodsVoteDomainException")
    void create_nullArtistId_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(null, "투표", FUTURE, NOW));
    }

    @Test
    @DisplayName("title null → GoodsVoteDomainException")
    void create_nullTitle_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(1L, null, FUTURE, NOW));
    }

    @Test
    @DisplayName("title 빈 문자열 → GoodsVoteDomainException")
    void create_blankTitle_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(1L, "  ", FUTURE, NOW));
    }

    @Test
    @DisplayName("title 256자 → GoodsVoteDomainException")
    void create_titleTooLong_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(1L, "a".repeat(256), FUTURE, NOW));
    }

    @Test
    @DisplayName("endsAt 현재 시각 이전 → GoodsVoteDomainException")
    void create_pastEndsAt_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(1L, "투표", PAST, NOW));
    }

    @Test
    @DisplayName("endsAt == now (strictly-after 아님) → GoodsVoteDomainException")
    void create_endsAtEqualsNow_throws() {
        assertThrows(GoodsVoteDomainException.class,
                () -> GoodsVote.create(1L, "투표", NOW, NOW));
    }

    @Test
    @DisplayName("isVotable — active=true, endsAt 미래 → true")
    void isVotable_activeAndFuture_returnsTrue() {
        GoodsVote vote = GoodsVote.reconstruct(1L, 1L, "투표", FUTURE, true, NOW);
        assertTrue(vote.isVotable(NOW));
    }

    @Test
    @DisplayName("isVotable — active=false → false")
    void isVotable_inactive_returnsFalse() {
        GoodsVote vote = GoodsVote.reconstruct(1L, 1L, "투표", FUTURE, false, NOW);
        assertFalse(vote.isVotable(NOW));
    }

    @Test
    @DisplayName("isVotable — endsAt 과거 → false")
    void isVotable_pastEndsAt_returnsFalse() {
        GoodsVote vote = GoodsVote.reconstruct(1L, 1L, "투표", PAST, true, NOW.minusDays(10));
        assertFalse(vote.isVotable(NOW));
    }

    @Test
    @DisplayName("close — active=false 새 인스턴스 반환, id·artistId 유지")
    void close_returnsInactiveInstance() {
        GoodsVote active = GoodsVote.reconstruct(1L, 10L, "투표", FUTURE, true, NOW);
        GoodsVote closed = active.close();
        assertFalse(closed.isActive());
        assertEquals(1L, closed.getId());
        assertEquals(10L, closed.getArtistId());
        assertTrue(active.isActive());  // 원본 불변
    }

    @Test
    @DisplayName("close — 이미 is_active=false인 투표도 close() 가능 (idempotent)")
    void close_alreadyInactive_succeeds() {
        GoodsVote inactive = GoodsVote.reconstruct(2L, 10L, "투표", FUTURE, false, NOW);
        GoodsVote closed = inactive.close();
        assertFalse(closed.isActive());
    }
}
