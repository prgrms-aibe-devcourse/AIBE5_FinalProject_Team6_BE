package com.fandrops.community.application.attendance;

import com.fandrops.community.application.exception.AlreadyCheckedInException;
import com.fandrops.community.application.exception.AttendanceEventNotFoundException;
import com.fandrops.community.application.exception.AttendanceEventNotOngoingException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.attendance.AttendanceEvent;
import com.fandrops.community.domain.attendance.AttendanceLog;
import com.fandrops.community.domain.attendance.exception.DuplicateAttendanceException;
import com.fandrops.community.domain.attendance.repository.AttendanceEventRepository;
import com.fandrops.community.domain.attendance.repository.AttendanceLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock AttendanceEventRepository eventRepository;
    @Mock AttendanceLogRepository logRepository;
    @Mock FanMembershipPort fanMembershipPort;

    AttendanceService attendanceService;

    // 2026-06-08 고정 시계
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 8);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-08T00:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(eventRepository, logRepository, fanMembershipPort, FIXED_CLOCK);
    }

    // -----------------------------------------------------------------------
    // getActiveEvents
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getActiveEvents")
    class GetActiveEventsTest {

        @Test
        @DisplayName("진행 중인 이벤트 목록 반환")
        void returnsActiveEvents() {
            AttendanceEvent event = event(1L, 10L, TODAY.minusDays(1), TODAY.plusDays(1));
            when(eventRepository.findActiveByArtistIdAndDate(10L, TODAY)).thenReturn(List.of(event));

            List<AttendanceEventResult> results = attendanceService.getActiveEvents(10L);

            assertEquals(1, results.size());
            assertEquals(1L, results.get(0).id());
        }

        @Test
        @DisplayName("진행 중인 이벤트가 없으면 빈 리스트 반환")
        void returnsEmptyWhenNone() {
            when(eventRepository.findActiveByArtistIdAndDate(10L, TODAY)).thenReturn(List.of());

            List<AttendanceEventResult> results = attendanceService.getActiveEvents(10L);

            assertTrue(results.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // checkIn
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("checkIn")
    class CheckInTest {

        @Test
        @DisplayName("정상 출석 체크 → CheckInResult 반환")
        void successfulCheckIn() {
            AttendanceEvent event = event(1L, 10L, TODAY, TODAY.plusDays(5));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(fanMembershipPort.isFanOf(99L, 10L)).thenReturn(true);
            when(logRepository.existsByEventIdAndFanIdAndCheckedDate(1L, 99L, TODAY)).thenReturn(false);

            AttendanceLog savedLog = AttendanceLog.reconstruct(100L, 1L, 99L, TODAY, LocalDateTime.now(FIXED_CLOCK));
            when(logRepository.save(any())).thenReturn(savedLog);
            when(logRepository.countByEventIdAndFanId(1L, 99L)).thenReturn(3);

            CheckInResult result = attendanceService.checkIn(1L, 99L);

            assertEquals(1L, result.eventId());
            assertEquals(TODAY, result.checkedDate());
            assertEquals(3, result.streakDays());
        }

        @Test
        @DisplayName("save 호출 시 event_id, fan_id, checked_date 올바르게 전달")
        void saveReceivesCorrectFields() {
            AttendanceEvent event = event(1L, 10L, TODAY, TODAY.plusDays(5));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(fanMembershipPort.isFanOf(99L, 10L)).thenReturn(true);
            when(logRepository.existsByEventIdAndFanIdAndCheckedDate(1L, 99L, TODAY)).thenReturn(false);

            AttendanceLog savedLog = AttendanceLog.reconstruct(100L, 1L, 99L, TODAY, LocalDateTime.now(FIXED_CLOCK));
            when(logRepository.save(any())).thenReturn(savedLog);
            when(logRepository.countByEventIdAndFanId(1L, 99L)).thenReturn(1);

            attendanceService.checkIn(1L, 99L);

            ArgumentCaptor<AttendanceLog> captor = ArgumentCaptor.forClass(AttendanceLog.class);
            verify(logRepository).save(captor.capture());
            AttendanceLog captured = captor.getValue();
            assertEquals(1L, captured.getEventId());
            assertEquals(99L, captured.getFanId());
            assertEquals(TODAY, captured.getCheckedDate());
        }

        @Test
        @DisplayName("존재하지 않는 이벤트 → AttendanceEventNotFoundException")
        void throwsWhenEventNotFound() {
            when(eventRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(AttendanceEventNotFoundException.class,
                    () -> attendanceService.checkIn(1L, 99L));

            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("종료된 이벤트 → AttendanceEventNotOngoingException")
        void throwsWhenEventNotOngoing() {
            AttendanceEvent expired = event(1L, 10L, TODAY.minusDays(10), TODAY.minusDays(1));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(expired));

            assertThrows(AttendanceEventNotOngoingException.class,
                    () -> attendanceService.checkIn(1L, 99L));

            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("동시 요청 경합 — save에서 DuplicateAttendanceException → AlreadyCheckedInException")
        void throwsAlreadyCheckedInOnRace() {
            AttendanceEvent event = event(1L, 10L, TODAY, TODAY.plusDays(5));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(fanMembershipPort.isFanOf(99L, 10L)).thenReturn(true);
            when(logRepository.existsByEventIdAndFanIdAndCheckedDate(1L, 99L, TODAY)).thenReturn(false);
            when(logRepository.save(any())).thenThrow(new DuplicateAttendanceException("오늘 이미 출석 체크를 완료했습니다."));

            assertThrows(AlreadyCheckedInException.class,
                    () -> attendanceService.checkIn(1L, 99L));
        }

        @Test
        @DisplayName("팬 미가입 → NotFanMemberException")
        void throwsWhenNotFanMember() {
            AttendanceEvent event = event(1L, 10L, TODAY, TODAY.plusDays(5));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(fanMembershipPort.isFanOf(99L, 10L)).thenReturn(false);

            assertThrows(NotFanMemberException.class,
                    () -> attendanceService.checkIn(1L, 99L));

            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("오늘 이미 출석 → AlreadyCheckedInException")
        void throwsWhenAlreadyCheckedIn() {
            AttendanceEvent event = event(1L, 10L, TODAY, TODAY.plusDays(5));
            when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(fanMembershipPort.isFanOf(99L, 10L)).thenReturn(true);
            when(logRepository.existsByEventIdAndFanIdAndCheckedDate(1L, 99L, TODAY)).thenReturn(true);

            assertThrows(AlreadyCheckedInException.class,
                    () -> attendanceService.checkIn(1L, 99L));

            verify(logRepository, never()).save(any());
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static AttendanceEvent event(Long id, Long artistId, LocalDate start, LocalDate end) {
        return AttendanceEvent.reconstruct(id, artistId, start, end, "보상 설명", true,
                LocalDateTime.now(FIXED_CLOCK));
    }
}