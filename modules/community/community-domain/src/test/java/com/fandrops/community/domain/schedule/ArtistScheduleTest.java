package com.fandrops.community.domain.schedule;

import com.fandrops.community.domain.schedule.exception.ScheduleDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ArtistScheduleTest {

    private static final LocalDateTime VALID_TIME = LocalDateTime.of(2026, 6, 1, 12, 0);

    @Test
    @DisplayName("유효한 인자 — 정상 생성")
    void create_success() {
        ArtistSchedule s = ArtistSchedule.create(1L, "팬미팅", ArtistScheduleType.EVENT, VALID_TIME);

        assertEquals(1L, s.getArtistId());
        assertEquals("팬미팅", s.getTitle());
        assertEquals(ArtistScheduleType.EVENT, s.getType());
        assertEquals(VALID_TIME, s.getScheduledAt());
        assertNull(s.getId());
    }

    @Test
    @DisplayName("artistId null → ScheduleDomainException")
    void create_nullArtistId_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.create(null, "팬미팅", ArtistScheduleType.EVENT, VALID_TIME));
    }

    @Test
    @DisplayName("title null → ScheduleDomainException")
    void create_nullTitle_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.create(1L, null, ArtistScheduleType.EVENT, VALID_TIME));
    }

    @Test
    @DisplayName("title 빈 문자열 → ScheduleDomainException")
    void create_blankTitle_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.create(1L, "   ", ArtistScheduleType.EVENT, VALID_TIME));
    }

    @Test
    @DisplayName("title 256자 → ScheduleDomainException")
    void create_titleTooLong_throws() {
        String longTitle = "a".repeat(256);
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.create(1L, longTitle, ArtistScheduleType.EVENT, VALID_TIME));
    }

    @Test
    @DisplayName("title 255자 — 경계값 정상 생성")
    void create_titleMaxLength_success() {
        String maxTitle = "a".repeat(255);
        ArtistSchedule s = ArtistSchedule.create(1L, maxTitle, ArtistScheduleType.EVENT, VALID_TIME);
        assertEquals(255, s.getTitle().length());
    }

    @Test
    @DisplayName("scheduledAt null → ScheduleDomainException")
    void create_nullScheduledAt_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.create(1L, "팬미팅", ArtistScheduleType.EVENT, null));
    }
}
