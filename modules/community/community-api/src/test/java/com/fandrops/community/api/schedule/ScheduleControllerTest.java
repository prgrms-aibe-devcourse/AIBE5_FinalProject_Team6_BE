package com.fandrops.community.api.schedule;

import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.exception.UnauthorizedException;
import com.fandrops.community.application.schedule.NoticeListResult;
import com.fandrops.community.application.schedule.NoticeResult;
import com.fandrops.community.application.schedule.ScheduleResult;
import com.fandrops.community.application.schedule.ScheduleService;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock ScheduleService scheduleService;
    @Mock Environment environment;

    ScheduleController controller;

    @BeforeEach
    void setUp() {
        controller = new ScheduleController(scheduleService, environment);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("POST /api/v1/artists/{artistId}/events")
    class CreateEventTest {

        private EventCreateRequest validRequest() {
            return new EventCreateRequest(
                    "팬미팅",
                    "EVENT",
                    OffsetDateTime.of(2026, 8, 10, 14, 0, 0, 0, ZoneOffset.UTC),
                    "https://ticket.example.com/123",
                    null);
        }

        @Test
        @DisplayName("로컬 프로필 + X-Artist-Member-Id 헤더 → 201, eventId 반환")
        void localProfile_artistMemberHeader_returns201() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            ScheduleResult stub = scheduleResult(10L, ArtistScheduleType.EVENT);
            when(scheduleService.createEvent(any())).thenReturn(stub);

            ResponseEntity<?> response = controller.createEvent(1L, validRequest(), null, 5L);

            assertEquals(201, response.getStatusCode().value());
            verify(scheduleService).createEvent(any());
        }

        @Test
        @DisplayName("ARTIST JWT (비로컬) → 201 반환")
        void artistJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("5", "ARTIST");
            ScheduleResult stub = scheduleResult(11L, ArtistScheduleType.EVENT);
            when(scheduleService.createEvent(any())).thenReturn(stub);

            ResponseEntity<?> response = controller.createEvent(1L, validRequest(), auth, null);

            assertEquals(201, response.getStatusCode().value());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → ForbiddenException, service 미호출")
        void noAuth_nonLocal_throwsForbidden() {
            assertThrows(ForbiddenException.class,
                    () -> controller.createEvent(1L, validRequest(), null, null));
            verify(scheduleService, never()).createEvent(any());
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → ForbiddenException, service 미호출")
        void fanJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("77", "FAN");

            assertThrows(ForbiddenException.class,
                    () -> controller.createEvent(1L, validRequest(), auth, null));
            verify(scheduleService, never()).createEvent(any());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/lives/{scheduleId}/start")
    class StartLiveTest {

        @Test
        @DisplayName("ARTIST JWT → 200, ScheduleResult 반환")
        void artistJwt_returns200() {
            Authentication auth = mockAuth("5", "ARTIST");
            ScheduleResult stub = scheduleResult(20L, ArtistScheduleType.LIVE);
            when(scheduleService.startLive(eq(20L), eq(5L))).thenReturn(stub);

            ResponseEntity<?> response = controller.startLive(20L, auth, null);

            assertEquals(200, response.getStatusCode().value());
            verify(scheduleService).startLive(eq(20L), eq(5L));
        }

        @Test
        @DisplayName("로컬 프로필 + X-Artist-Member-Id → 200 반환")
        void localProfile_artistMemberHeader_returns200() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            ScheduleResult stub = scheduleResult(20L, ArtistScheduleType.LIVE);
            when(scheduleService.startLive(eq(20L), eq(5L))).thenReturn(stub);

            ResponseEntity<?> response = controller.startLive(20L, null, 5L);

            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → ForbiddenException, service 미호출")
        void fanJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("77", "FAN");

            assertThrows(ForbiddenException.class,
                    () -> controller.startLive(20L, auth, null));
            verify(scheduleService, never()).startLive(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 scheduleId (잘못된 입력) → ScheduleNotFoundException 전파")
        void invalidScheduleId_propagates() {
            Authentication auth = mockAuth("5", "ARTIST");
            when(scheduleService.startLive(eq(999L), eq(5L)))
                    .thenThrow(new ScheduleNotFoundException("스케줄을 찾을 수 없습니다."));

            assertThrows(ScheduleNotFoundException.class,
                    () -> controller.startLive(999L, auth, null));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/artists/{artistId}/lives")
    class RegisterLiveTest {

        private LiveCreateRequest validRequest() {
            return new LiveCreateRequest(
                    "팬 라이브 방송",
                    OffsetDateTime.of(2026, 9, 1, 18, 0, 0, 0, ZoneOffset.UTC),
                    "https://www.youtube.com/embed/dQw4w9WgXcQ");
        }

        @Test
        @DisplayName("AGENCY JWT (비로컬) → 201, scheduleId 반환")
        void agencyJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("5", "AGENCY");
            ScheduleResult stub = scheduleResult(30L, ArtistScheduleType.LIVE);
            when(scheduleService.registerLive(any())).thenReturn(stub);

            ResponseEntity<?> response = controller.registerLive(1L, validRequest(), auth, null);

            assertEquals(201, response.getStatusCode().value());
        }

        @Test
        @DisplayName("ARTIST JWT (비로컬) → ForbiddenException (AGENCY 전용)")
        void artistJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("5", "ARTIST");

            assertThrows(ForbiddenException.class,
                    () -> controller.registerLive(1L, validRequest(), auth, null));
            verify(scheduleService, never()).registerLive(any());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → ForbiddenException")
        void noAuth_nonLocal_throwsForbidden() {
            assertThrows(ForbiddenException.class,
                    () -> controller.registerLive(1L, validRequest(), null, null));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/artists/{artistId}/calendar")
    class GetCalendarTest {

        @Test
        @DisplayName("공개 조회 (날짜 필터 없음) → 200, 이벤트 목록 반환")
        void public_noFilter_returnsEvents() {
            ScheduleResult stub = scheduleResult(1L, ArtistScheduleType.EVENT);
            when(scheduleService.getCalendar(eq(10L), isNull(), isNull()))
                    .thenReturn(List.of(stub));

            ResponseEntity<?> response = controller.getCalendar(10L, null, null);

            assertEquals(200, response.getStatusCode().value());
            verify(scheduleService).getCalendar(eq(10L), isNull(), isNull());
        }

        @Test
        @DisplayName("공개 조회 (빈 결과) → 200, 빈 목록")
        void public_emptyResult_returns200() {
            when(scheduleService.getCalendar(eq(99L), isNull(), isNull())).thenReturn(List.of());

            ResponseEntity<?> response = controller.getCalendar(99L, null, null);

            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/artists/{artistId}/notices")
    class GetNoticesTest {

        @Test
        @DisplayName("공개 조회 → 200, 커서 페이징 반환")
        void public_returnsNoticeList() {
            NoticeListResult stub = new NoticeListResult(List.of(), null, false);
            when(scheduleService.getNotices(eq(10L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getNotices(10L, null, 20);

            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("공지 단건 조회 → 200, NoticeResult 반환")
        void getNotice_returns200() {
            NoticeResult stub = new NoticeResult(
                    1L, ArtistScheduleType.NOTICE, "공지 제목", "공지 내용",
                    List.of(), OffsetDateTime.now(ZoneOffset.UTC));
            when(scheduleService.getNotice(eq(10L), eq(1L))).thenReturn(stub);

            ResponseEntity<?> response = controller.getNotice(10L, 1L);

            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("존재하지 않는 공지 (잘못된 입력) → ScheduleNotFoundException 전파")
        void invalidNoticeId_propagates() {
            when(scheduleService.getNotice(eq(10L), eq(999L)))
                    .thenThrow(new ScheduleNotFoundException("공지를 찾을 수 없습니다."));

            assertThrows(ScheduleNotFoundException.class,
                    () -> controller.getNotice(10L, 999L));
        }
    }

    private static ScheduleResult scheduleResult(Long id, ArtistScheduleType type) {
        return new ScheduleResult(id, type, "제목", OffsetDateTime.now(ZoneOffset.UTC), null, null, null);
    }

    private static Authentication mockAuth(String name, String authority) {
        return new Authentication() {
            @Override public Collection<? extends GrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority(authority));
            }
            @Override public Object getCredentials() { return null; }
            @Override public Object getDetails() { return null; }
            @Override public Object getPrincipal() { return name; }
            @Override public boolean isAuthenticated() { return true; }
            @Override public void setAuthenticated(boolean b) {}
            @Override public String getName() { return name; }
        };
    }
}