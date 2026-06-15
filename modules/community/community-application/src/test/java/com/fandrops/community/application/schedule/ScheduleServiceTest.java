package com.fandrops.community.application.schedule;

import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.application.port.ScheduleImagePort;
import org.springframework.context.ApplicationEventPublisher;
import com.fandrops.community.domain.schedule.ArtistSchedule;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.repository.ArtistScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock ArtistScheduleRepository scheduleRepository;
    @Mock OutboxEventPort outboxEventPort;
    @Mock ApplicationEventPublisher applicationEventPublisher;
    @Mock ScheduleImagePort scheduleImagePort;

    ScheduleService scheduleService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 0, 0, 0);

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, outboxEventPort,
                applicationEventPublisher, scheduleImagePort);
    }

    @Nested
    @DisplayName("getCalendar")
    class GetCalendarTest {

        @Test
        @DisplayName("from·to 범위 내 스케줄 목록 반환")
        void returnsSchedulesInRange() {
            ArtistSchedule s1 = schedule(1L, 10L, ArtistScheduleType.LIVE, "콘서트", NOW);
            ArtistSchedule s2 = schedule(2L, 10L, ArtistScheduleType.DROP, "드롭", NOW.plusDays(1));
            when(scheduleRepository.findByArtistIdBetween(eq(10L), eq(NOW), eq(NOW.plusDays(7))))
                    .thenReturn(List.of(s1, s2));

            List<ScheduleResult> result = scheduleService.getCalendar(10L, NOW, NOW.plusDays(7));

            assertEquals(2, result.size());
            assertEquals(1L, result.get(0).id());
            assertEquals(ArtistScheduleType.LIVE, result.get(0).type());
            assertEquals("콘서트", result.get(0).title());
        }

        @Test
        @DisplayName("from·to 모두 null — 기본값(now-30d, now+90d) 적용 후 조회")
        void nullRange_appliesDefaults() {
            when(scheduleRepository.findByArtistIdBetween(eq(10L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            List<ScheduleResult> result = scheduleService.getCalendar(10L, null, null);

            assertTrue(result.isEmpty());
            verify(scheduleRepository).findByArtistIdBetween(eq(10L), any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("스케줄 없는 경우 빈 리스트 반환")
        void noSchedules_returnsEmpty() {
            when(scheduleRepository.findByArtistIdBetween(eq(10L), any(), any()))
                    .thenReturn(List.of());

            List<ScheduleResult> result = scheduleService.getCalendar(10L, NOW, NOW.plusDays(7));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("createEvent")
    class CreateEventTest {

        @Test
        @DisplayName("유효한 커맨드 — save 호출 후 ScheduleResult 반환")
        void success() {
            ArtistSchedule saved = schedule(1L, 10L, ArtistScheduleType.EVENT, "팬미팅", NOW.plusDays(30));
            when(scheduleRepository.save(any())).thenReturn(saved);

            ScheduleResult result = scheduleService.createEvent(
                    new EventCreateCommand(10L, 5L, "팬미팅", "EVENT", NOW.plusDays(30).atOffset(ZoneOffset.UTC)));

            assertEquals(1L, result.id());
            assertEquals(ArtistScheduleType.EVENT, result.type());
            assertEquals("팬미팅", result.title());
            verify(scheduleRepository).save(any());
        }

        @Test
        @DisplayName("알 수 없는 type 문자열 → IllegalArgumentException")
        void unknownType_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> scheduleService.createEvent(
                            new EventCreateCommand(10L, 5L, "행사", "UNKNOWN", NOW.plusDays(1).atOffset(ZoneOffset.UTC))));
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("artistMemberId null → NullPointerException (service-level null 가드)")
        void nullArtistMemberId_throws() {
            assertThrows(NullPointerException.class,
                    () -> scheduleService.createEvent(
                            new EventCreateCommand(10L, null, "행사", "EVENT", NOW.plusDays(1).atOffset(ZoneOffset.UTC))));
            verify(scheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("startLive")
    class StartLiveTest {

        @Test
        @DisplayName("LIVE 타입 일정 → outbox LIVE_START 이벤트 발행 후 ScheduleResult 반환")
        void liveSchedule_publishesAndReturns() {
            ArtistSchedule live = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(live));
            when(outboxEventPort.existsEvent(eq(1L), eq(OutboxEventType.LIVE_START))).thenReturn(false);

            ScheduleResult result = scheduleService.startLive(1L, 5L);

            assertEquals(1L, result.id());
            assertEquals(ArtistScheduleType.LIVE, result.type());
            verify(outboxEventPort).publish(argThat(e ->
                    OutboxEventType.LIVE_START == e.type() && e.aggregateId().equals(1L)));
        }

        @Test
        @DisplayName("이미 발행된 LIVE_START 이벤트 존재 → publish 생략, ScheduleResult 반환 (멱등)")
        void alreadyPublished_skipsPublish() {
            ArtistSchedule live = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(live));
            when(outboxEventPort.existsEvent(eq(1L), eq(OutboxEventType.LIVE_START))).thenReturn(true);

            ScheduleResult result = scheduleService.startLive(1L, 5L);

            assertEquals(1L, result.id());
            verify(outboxEventPort, never()).publish(any());
        }

        @Test
        @DisplayName("존재하지 않는 scheduleId → ScheduleNotFoundException")
        void notFound_throws() {
            when(scheduleRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(ScheduleNotFoundException.class, () -> scheduleService.startLive(999L, 5L));
            verifyNoInteractions(outboxEventPort);
        }

        @Test
        @DisplayName("LIVE 타입이 아닌 일정 → IllegalArgumentException, outbox 발행 안 함")
        void nonLiveType_throws() {
            ArtistSchedule drop = schedule(2L, 10L, ArtistScheduleType.DROP, "드롭", NOW);
            when(scheduleRepository.findById(eq(2L))).thenReturn(Optional.of(drop));

            assertThrows(IllegalArgumentException.class, () -> scheduleService.startLive(2L, 5L));
            verifyNoInteractions(outboxEventPort);
        }
    }

    @Nested
    @DisplayName("registerLive")
    class RegisterLiveTest {

        @Test
        @DisplayName("유효한 커맨드 — save 호출 후 LIVE ScheduleResult 반환")
        void success() {
            ArtistSchedule saved = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브 방송",
                    NOW.plusDays(1), "https://www.youtube.com/embed/abc123");
            when(scheduleRepository.save(any())).thenReturn(saved);

            ScheduleResult result = scheduleService.registerLive(
                    new LiveCreateCommand(10L, 5L, "라이브 방송",
                            NOW.plusDays(1).atOffset(ZoneOffset.UTC),
                            "https://www.youtube.com/embed/abc123"));

            assertEquals(1L, result.id());
            assertEquals(ArtistScheduleType.LIVE, result.type());
            assertEquals("라이브 방송", result.title());
            assertEquals("https://www.youtube.com/embed/abc123", result.liveUrl());
            verify(scheduleRepository).save(any());
        }

        @Test
        @DisplayName("liveUrl null 허용 — save 정상 동작")
        void nullLiveUrl_allowed() {
            ArtistSchedule saved = schedule(2L, 10L, ArtistScheduleType.LIVE, "라이브",
                    NOW, null);
            when(scheduleRepository.save(any())).thenReturn(saved);

            ScheduleResult result = scheduleService.registerLive(
                    new LiveCreateCommand(10L, 5L, "라이브",
                            NOW.atOffset(ZoneOffset.UTC), null));

            assertNull(result.liveUrl());
            verify(scheduleRepository).save(any());
        }

        @Test
        @DisplayName("artistMemberId null → NullPointerException, save 호출 안 함")
        void nullArtistMemberId_throws() {
            assertThrows(NullPointerException.class,
                    () -> scheduleService.registerLive(
                            new LiveCreateCommand(10L, null, "라이브",
                                    NOW.atOffset(ZoneOffset.UTC), null)));
            verify(scheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getLives")
    class GetLivesTest {

        @Test
        @DisplayName("artistId에 해당하는 LIVE 일정 목록 반환")
        void returnsLiveSchedules() {
            ArtistSchedule l1 = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브1",
                    NOW, "https://www.youtube.com/embed/aaa");
            ArtistSchedule l2 = schedule(2L, 10L, ArtistScheduleType.LIVE, "라이브2",
                    NOW.plusHours(2), null);
            when(scheduleRepository.findLivesByArtistId(eq(10L))).thenReturn(List.of(l1, l2));

            List<ScheduleResult> result = scheduleService.getLives(10L);

            assertEquals(2, result.size());
            assertEquals(ArtistScheduleType.LIVE, result.get(0).type());
            assertEquals("https://www.youtube.com/embed/aaa", result.get(0).liveUrl());
            assertNull(result.get(1).liveUrl());
        }

        @Test
        @DisplayName("LIVE 일정 없을 경우 빈 리스트 반환")
        void noLives_returnsEmpty() {
            when(scheduleRepository.findLivesByArtistId(eq(10L))).thenReturn(List.of());

            List<ScheduleResult> result = scheduleService.getLives(10L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("createNotice")
    class CreateNoticeTest {

        @Test
        @DisplayName("유효한 커맨드 — save 호출 후 NoticeResult 반환")
        void success() {
            ArtistSchedule saved = notice(1L, 10L, "팬미팅 공지", "내용입니다", NOW);
            when(scheduleRepository.save(any())).thenReturn(saved);
            when(scheduleImagePort.findByScheduleId(eq(1L)))
                    .thenReturn(List.of("https://cdn.example.com/img1.jpg"));

            NoticeResult result = scheduleService.createNotice(
                    new NoticeCreateCommand(10L, 5L, "팬미팅 공지", "내용입니다",
                            List.of("https://cdn.example.com/img1.jpg"),
                            NOW.atOffset(ZoneOffset.UTC)));

            assertEquals(1L, result.id());
            assertEquals(ArtistScheduleType.NOTICE, result.type());
            assertEquals("팬미팅 공지", result.title());
            assertEquals("내용입니다", result.content());
            verify(scheduleRepository).save(any());
            verify(scheduleImagePort).saveAll(eq(1L), eq(List.of("https://cdn.example.com/img1.jpg")));
        }

        @Test
        @DisplayName("imageUrls 비어 있으면 saveAll 미호출")
        void emptyImages_noSaveAllCall() {
            ArtistSchedule saved = notice(2L, 10L, "공지", null, NOW);
            when(scheduleRepository.save(any())).thenReturn(saved);

            scheduleService.createNotice(
                    new NoticeCreateCommand(10L, 5L, "공지", null, List.of(), NOW.atOffset(ZoneOffset.UTC)));

            verify(scheduleImagePort, never()).saveAll(any(), any());
        }

        @Test
        @DisplayName("artistMemberId null → NullPointerException, save 호출 안 함")
        void nullArtistMemberId_throws() {
            assertThrows(NullPointerException.class,
                    () -> scheduleService.createNotice(
                            new NoticeCreateCommand(10L, null, "공지", null, null,
                                    NOW.atOffset(ZoneOffset.UTC))));
            verify(scheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("scheduledAt null — 현재 시각으로 기본 처리")
        void nullScheduledAt_defaultsToNow() {
            ArtistSchedule saved = notice(3L, 10L, "즉시 공지", null, NOW);
            when(scheduleRepository.save(any())).thenReturn(saved);

            NoticeResult result = scheduleService.createNotice(
                    new NoticeCreateCommand(10L, 5L, "즉시 공지", null, null, null));

            assertNotNull(result);
            verify(scheduleRepository).save(any());
        }
    }

    @Nested
    @DisplayName("getNotices")
    class GetNoticesTest {

        @Test
        @DisplayName("커서 없음 — 첫 페이지 반환")
        void firstPage_noHasMore() {
            ArtistSchedule n1 = notice(2L, 10L, "공지2", null, NOW);
            ArtistSchedule n2 = notice(1L, 10L, "공지1", null, NOW.minusDays(1));
            when(scheduleRepository.findNoticesByArtistId(eq(10L), isNull(), eq(3)))
                    .thenReturn(List.of(n1, n2));
            when(scheduleImagePort.findByScheduleIds(List.of(2L, 1L))).thenReturn(Map.of());

            NoticeListResult result = scheduleService.getNotices(10L, null, 2);

            assertEquals(2, result.items().size());
            assertFalse(result.hasMore());
            assertNull(result.nextCursor());
        }

        @Test
        @DisplayName("size+1 개 반환 — hasMore true, nextCursor 설정")
        void hasMore_true() {
            ArtistSchedule n1 = notice(3L, 10L, "공지3", null, NOW);
            ArtistSchedule n2 = notice(2L, 10L, "공지2", null, NOW.minusDays(1));
            ArtistSchedule n3 = notice(1L, 10L, "공지1", null, NOW.minusDays(2));
            when(scheduleRepository.findNoticesByArtistId(eq(10L), isNull(), eq(3)))
                    .thenReturn(List.of(n1, n2, n3));
            when(scheduleImagePort.findByScheduleIds(List.of(3L, 2L))).thenReturn(Map.of());

            NoticeListResult result = scheduleService.getNotices(10L, null, 2);

            assertEquals(2, result.items().size());
            assertTrue(result.hasMore());
            assertEquals("2", result.nextCursor());
        }

        @Test
        @DisplayName("잘못된 cursor 형식 → IllegalArgumentException")
        void invalidCursor_throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> scheduleService.getNotices(10L, "invalid", 20));
        }
    }

    @Nested
    @DisplayName("getNotice")
    class GetNoticeTest {

        @Test
        @DisplayName("존재하는 NOTICE → NoticeResult 반환")
        void success() {
            ArtistSchedule n = notice(1L, 10L, "공지", "내용", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(n));
            when(scheduleImagePort.findByScheduleId(eq(1L)))
                    .thenReturn(List.of("https://cdn.example.com/img.jpg"));

            NoticeResult result = scheduleService.getNotice(10L, 1L);

            assertEquals(1L, result.id());
            assertEquals("공지", result.title());
            assertEquals("내용", result.content());
            assertEquals(List.of("https://cdn.example.com/img.jpg"), result.imageUrls());
        }

        @Test
        @DisplayName("존재하지 않는 noticeId → ScheduleNotFoundException")
        void notFound_throws() {
            when(scheduleRepository.findById(eq(999L))).thenReturn(Optional.empty());
            assertThrows(ScheduleNotFoundException.class, () -> scheduleService.getNotice(10L, 999L));
        }

        @Test
        @DisplayName("NOTICE 타입이 아닌 id → ScheduleNotFoundException")
        void notNoticeType_throws() {
            ArtistSchedule live = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(live));
            assertThrows(ScheduleNotFoundException.class, () -> scheduleService.getNotice(10L, 1L));
        }

        @Test
        @DisplayName("다른 아티스트 공지 접근 → ScheduleNotFoundException (IDOR)")
        void wrongArtistId_throws() {
            ArtistSchedule notice = notice(1L, 10L, "공지", null, NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(notice));
            assertThrows(ScheduleNotFoundException.class, () -> scheduleService.getNotice(99L, 1L));
        }
    }

    private static ArtistSchedule schedule(Long id, Long artistId, ArtistScheduleType type,
                                            String title, LocalDateTime scheduledAt) {
        return ArtistSchedule.reconstruct(id, artistId, null, title, type, scheduledAt, null);
    }

    private static ArtistSchedule schedule(Long id, Long artistId, ArtistScheduleType type,
                                            String title, LocalDateTime scheduledAt, String liveUrl) {
        return ArtistSchedule.reconstruct(id, artistId, null, title, type, scheduledAt, liveUrl);
    }

    private static ArtistSchedule notice(Long id, Long artistId, String title,
                                          String content, LocalDateTime scheduledAt) {
        return ArtistSchedule.reconstruct(id, artistId, null, title,
                ArtistScheduleType.NOTICE, scheduledAt, null, content);
    }
}