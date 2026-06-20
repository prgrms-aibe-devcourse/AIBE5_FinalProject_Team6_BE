package com.fandrops.community.api.attendance;

import com.fandrops.community.application.attendance.AttendanceEventResult;
import com.fandrops.community.application.attendance.AttendanceService;
import com.fandrops.community.application.attendance.CheckInResult;
import com.fandrops.community.application.exception.AlreadyCheckedInException;
import com.fandrops.community.application.exception.AttendanceEventNotOngoingException;
import com.fandrops.community.application.exception.UnauthorizedException;
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

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock AttendanceService attendanceService;
    @Mock Environment environment;

    AttendanceController controller;

    @BeforeEach
    void setUp() {
        controller = new AttendanceController(attendanceService, environment);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("GET /api/v1/artists/{artistId}/attendance-events")
    class GetActiveEventsTest {

        @Test
        @DisplayName("공개 조회 → 200, 진행 중 이벤트 목록 반환")
        void public_returnsActiveEvents() {
            AttendanceEventResult event = new AttendanceEventResult(
                    1L,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 30),
                    "7일 출석 시 포토카드 증정");
            when(attendanceService.getActiveEvents(eq(10L))).thenReturn(List.of(event));

            ResponseEntity<?> response = controller.getActiveEvents(10L);

            assertEquals(200, response.getStatusCode().value());
            verify(attendanceService).getActiveEvents(eq(10L));
        }

        @Test
        @DisplayName("진행 중 이벤트 없음 → 200, 빈 목록 반환")
        void noActiveEvents_returnsEmptyList() {
            when(attendanceService.getActiveEvents(eq(99L))).thenReturn(List.of());

            ResponseEntity<?> response = controller.getActiveEvents(99L);

            assertEquals(200, response.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/attendance-events/{eventId}/check-in")
    class CheckInTest {

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 201, 출석 결과 반환")
        void localProfile_fanHeader_returns201() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            CheckInResult stub = new CheckInResult(1L, LocalDate.of(2026, 6, 19), 3);
            when(attendanceService.checkIn(eq(1L), eq(55L))).thenReturn(stub);

            ResponseEntity<?> response = controller.checkIn(1L, null, 55L);

            assertEquals(201, response.getStatusCode().value());
            verify(attendanceService).checkIn(eq(1L), eq(55L));
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → 201, 출석 결과 반환")
        void fanJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("77", "FAN");
            CheckInResult stub = new CheckInResult(1L, LocalDate.of(2026, 6, 19), 1);
            when(attendanceService.checkIn(eq(1L), eq(77L))).thenReturn(stub);

            ResponseEntity<?> response = controller.checkIn(1L, auth, null);

            assertEquals(201, response.getStatusCode().value());
            verify(attendanceService).checkIn(eq(1L), eq(77L));
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException, service 미호출")
        void noAuth_nonLocal_throwsUnauthorized() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.checkIn(1L, null, null));
            verify(attendanceService, never()).checkIn(any(), any());
        }

        @Test
        @DisplayName("이미 출석 체크 (잘못된 입력) → AlreadyCheckedInException 전파")
        void alreadyCheckedIn_propagates() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(attendanceService.checkIn(eq(1L), eq(55L)))
                    .thenThrow(new AlreadyCheckedInException("오늘 이미 출석 체크 하였습니다."));

            assertThrows(AlreadyCheckedInException.class,
                    () -> controller.checkIn(1L, null, 55L));
        }

        @Test
        @DisplayName("종료된 이벤트 → AttendanceEventNotOngoingException 전파")
        void expiredEvent_propagates() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(attendanceService.checkIn(eq(999L), eq(55L)))
                    .thenThrow(new AttendanceEventNotOngoingException("진행 중인 출석 이벤트가 아닙니다."));

            assertThrows(AttendanceEventNotOngoingException.class,
                    () -> controller.checkIn(999L, null, 55L));
        }
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