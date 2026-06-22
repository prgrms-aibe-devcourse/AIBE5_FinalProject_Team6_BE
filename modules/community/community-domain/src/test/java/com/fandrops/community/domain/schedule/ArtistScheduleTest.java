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

    @Test
    @DisplayName("createEvent — externalTicketUrl 포함 정상 생성")
    void createEvent_withUrl_success() {
        ArtistSchedule s = ArtistSchedule.createEvent(
                1L, "팬미팅", VALID_TIME, "https://ticket.example.com/123");

        assertEquals(ArtistScheduleType.EVENT, s.getType());
        assertEquals("https://ticket.example.com/123", s.getExternalTicketUrl());
        assertNull(s.getLiveUrl());
    }

    @Test
    @DisplayName("createEvent — externalTicketUrl null 허용")
    void createEvent_nullUrl_success() {
        ArtistSchedule s = ArtistSchedule.createEvent(1L, "팬미팅", VALID_TIME, null);

        assertEquals(ArtistScheduleType.EVENT, s.getType());
        assertNull(s.getExternalTicketUrl());
    }

    @Test
    @DisplayName("createLinkedSchedule — noticeId 연결, DROP 타입 정상 생성")
    void createLinkedSchedule_drop_success() {
        ArtistSchedule s = ArtistSchedule.createLinkedSchedule(
                1L, "드롭 일정", ArtistScheduleType.DROP, VALID_TIME, 42L);

        assertEquals(ArtistScheduleType.DROP, s.getType());
        assertEquals(42L, s.getNoticeId());
        assertNull(s.getId());
    }

    @Test
    @DisplayName("createLinkedSchedule — LIVE 타입 + noticeId 정상 생성")
    void createLinkedSchedule_live_success() {
        ArtistSchedule s = ArtistSchedule.createLinkedSchedule(
                1L, "라이브 예고", ArtistScheduleType.LIVE, VALID_TIME, 99L);

        assertEquals(ArtistScheduleType.LIVE, s.getType());
        assertEquals(99L, s.getNoticeId());
    }

    @Test
    @DisplayName("createLinkedSchedule — artistId null → ScheduleDomainException")
    void createLinkedSchedule_nullArtistId_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.createLinkedSchedule(
                        null, "타이틀", ArtistScheduleType.EVENT, VALID_TIME, 1L));
    }

    @Test
    @DisplayName("createLinkedSchedule — NOTICE 타입 → ScheduleDomainException")
    void createLinkedSchedule_noticeType_throws() {
        assertThrows(ScheduleDomainException.class,
                () -> ArtistSchedule.createLinkedSchedule(
                        1L, "타이틀", ArtistScheduleType.NOTICE, VALID_TIME, 1L));
    }

    @Test
    @DisplayName("createEvent(noticeId 포함) — noticeId 연결된 EVENT 생성")
    void createEvent_withNoticeId_success() {
        ArtistSchedule s = ArtistSchedule.createEvent(
                1L, "팬미팅", VALID_TIME, "https://ticket.example.com/1", 77L);

        assertEquals(ArtistScheduleType.EVENT, s.getType());
        assertEquals("https://ticket.example.com/1", s.getExternalTicketUrl());
        assertEquals(77L, s.getNoticeId());
    }
}
