package com.fandrops.community.api.mypage;

import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.exception.UnauthorizedException;
import com.fandrops.community.application.mypage.ActivityListResult;
import com.fandrops.community.application.mypage.FanActivityService;
import com.fandrops.community.application.mypage.JoinedArtistListResult;
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

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FanMypageControllerTest {

    @Mock FanActivityService fanActivityService;
    @Mock Environment environment;

    FanMypageController controller;

    @BeforeEach
    void setUp() {
        controller = new FanMypageController(fanActivityService, environment);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("GET /api/v1/fans/me/activities")
    class GetActivitiesTest {

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 200, 활동 목록 반환")
        void localProfile_fanHeader_returns200() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            ActivityListResult stub = new ActivityListResult(List.of(), null, false);
            when(fanActivityService.getActivities(eq(55L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getActivities(null, 20, null, 55L);

            assertEquals(200, response.getStatusCode().value());
            verify(fanActivityService).getActivities(eq(55L), isNull(), eq(20));
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → 200, 활동 목록 반환")
        void fanJwt_nonLocal_returns200() {
            Authentication auth = mockAuth("77", "ROLE_FAN");
            ActivityListResult stub = new ActivityListResult(List.of(), null, false);
            when(fanActivityService.getActivities(eq(77L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getActivities(null, 20, auth, null);

            assertEquals(200, response.getStatusCode().value());
            verify(fanActivityService).getActivities(eq(77L), isNull(), eq(20));
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException, service 미호출")
        void noAuth_nonLocal_throwsUnauthorized() {
            // assertFanRole: auth==null → 통과. resolveFanId: auth==null && header==null → UnauthorizedException
            assertThrows(UnauthorizedException.class,
                    () -> controller.getActivities(null, 20, null, null));
            verify(fanActivityService, never()).getActivities(any(), any(), anyInt());
        }

        @Test
        @DisplayName("AGENCY JWT (비로컬) → ForbiddenException (팬 계정 전용), service 미호출")
        void agencyJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("5", "ROLE_AGENCY");

            assertThrows(ForbiddenException.class,
                    () -> controller.getActivities(null, 20, auth, null));
            verify(fanActivityService, never()).getActivities(any(), any(), anyInt());
        }

        @Test
        @DisplayName("커서 + size 지정 → service에 그대로 전달")
        void withCursorAndSize_passesToService() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            ActivityListResult stub = new ActivityListResult(List.of(), null, false);
            when(fanActivityService.getActivities(eq(55L), eq("cursor-abc"), eq(10))).thenReturn(stub);

            ResponseEntity<?> response = controller.getActivities("cursor-abc", 10, null, 55L);

            assertEquals(200, response.getStatusCode().value());
            verify(fanActivityService).getActivities(eq(55L), eq("cursor-abc"), eq(10));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/fans/me/artists")
    class GetJoinedArtistsTest {

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 200, 가입 아티스트 목록 반환")
        void localProfile_fanHeader_returns200() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            JoinedArtistListResult stub = new JoinedArtistListResult(List.of(), null, false);
            when(fanActivityService.getJoinedArtists(eq(55L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getJoinedArtists(null, 20, null, 55L);

            assertEquals(200, response.getStatusCode().value());
            verify(fanActivityService).getJoinedArtists(eq(55L), isNull(), eq(20));
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → 200, 가입 아티스트 목록 반환")
        void fanJwt_nonLocal_returns200() {
            Authentication auth = mockAuth("77", "ROLE_FAN");
            JoinedArtistListResult stub = new JoinedArtistListResult(List.of(), null, false);
            when(fanActivityService.getJoinedArtists(eq(77L), isNull(), eq(20))).thenReturn(stub);

            ResponseEntity<?> response = controller.getJoinedArtists(null, 20, auth, null);

            assertEquals(200, response.getStatusCode().value());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException, service 미호출")
        void noAuth_nonLocal_throwsUnauthorized() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.getJoinedArtists(null, 20, null, null));
            verify(fanActivityService, never()).getJoinedArtists(any(), any(), anyInt());
        }

        @Test
        @DisplayName("ARTIST JWT (비로컬) → ForbiddenException (팬 계정 전용), service 미호출")
        void artistJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("5", "ROLE_ARTIST");

            assertThrows(ForbiddenException.class,
                    () -> controller.getJoinedArtists(null, 20, auth, null));
            verify(fanActivityService, never()).getJoinedArtists(any(), any(), anyInt());
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