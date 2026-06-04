package com.fandrops.community.application.schedule;

import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.port.ScheduleNotificationPort;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock ArtistScheduleRepository scheduleRepository;
    @Mock ScheduleNotificationPort notificationPort;

    ScheduleService scheduleService;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 0, 0, 0);

    @BeforeEach
    void setUp() {
        scheduleService = new ScheduleService(scheduleRepository, notificationPort);
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
        @DisplayName("from·to 모두 null — 전체 조회 위임")
        void nullRange_delegatesToRepository() {
            when(scheduleRepository.findByArtistIdBetween(eq(10L), isNull(), isNull()))
                    .thenReturn(List.of());

            List<ScheduleResult> result = scheduleService.getCalendar(10L, null, null);

            assertTrue(result.isEmpty());
            verify(scheduleRepository).findByArtistIdBetween(eq(10L), isNull(), isNull());
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
    @DisplayName("startLive")
    class StartLiveTest {

        @Test
        @DisplayName("LIVE 타입 일정 시작 성공 — isLive=true, 알림 발행, save 호출")
        void success() {
            ArtistSchedule liveSchedule = schedule(1L, 10L, ArtistScheduleType.LIVE, "라이브", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(liveSchedule));
            when(scheduleRepository.save(any())).thenReturn(liveSchedule);

            LiveStartResult result = scheduleService.startLive(1L, 5L);

            assertTrue(result.isLive());
            assertEquals(1L, result.liveId());
            verify(scheduleRepository).save(any());
            verify(notificationPort).notifyLiveStarted(eq(10L), eq(1L), eq("라이브"));
        }

        @Test
        @DisplayName("존재하지 않는 liveId → ScheduleNotFoundException")
        void notFound_throws() {
            when(scheduleRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(ScheduleNotFoundException.class,
                    () -> scheduleService.startLive(999L, 5L));
            verify(scheduleRepository, never()).save(any());
            verifyNoInteractions(notificationPort);
        }

        @Test
        @DisplayName("이미 라이브 중인 일정 재시작 시도 → IllegalArgumentException (멱등 가드)")
        void alreadyLive_throws() {
            ArtistSchedule alreadyLive = ArtistSchedule.reconstruct(
                    1L, 10L, null, "라이브", ArtistScheduleType.LIVE, NOW, true);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(alreadyLive));

            assertThrows(IllegalArgumentException.class,
                    () -> scheduleService.startLive(1L, 5L));
            verify(scheduleRepository, never()).save(any());
            verifyNoInteractions(notificationPort);
        }

        @Test
        @DisplayName("LIVE 아닌 타입(DROP) 시작 시도 → IllegalArgumentException (ScheduleDomainException 래핑)")
        void nonLiveType_throws() {
            ArtistSchedule dropSchedule = schedule(1L, 10L, ArtistScheduleType.DROP, "드롭", NOW);
            when(scheduleRepository.findById(eq(1L))).thenReturn(Optional.of(dropSchedule));

            assertThrows(IllegalArgumentException.class,
                    () -> scheduleService.startLive(1L, 5L));
            verify(scheduleRepository, never()).save(any());
            verifyNoInteractions(notificationPort);
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
                    new EventCreateCommand(10L, 5L, "팬미팅", "EVENT", NOW.plusDays(30)));

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
                            new EventCreateCommand(10L, 5L, "행사", "UNKNOWN", NOW.plusDays(1))));
            verify(scheduleRepository, never()).save(any());
        }
    }

    private static ArtistSchedule schedule(Long id, Long artistId, ArtistScheduleType type,
                                            String title, LocalDateTime scheduledAt) {
        return ArtistSchedule.reconstruct(id, artistId, null, title, type, scheduledAt, false);
    }
}