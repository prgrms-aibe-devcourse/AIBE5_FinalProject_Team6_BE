package com.fandrops.community.api.artist;

import com.fandrops.community.application.exception.AlreadyJoinedException;
import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.exception.UnauthorizedException;
import com.fandrops.community.application.follow.FanJoinResult;
import com.fandrops.community.application.follow.FanJoinService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArtistFollowControllerTest {

    @Mock FanJoinService fanJoinService;
    @Mock Environment environment;

    ArtistFollowController controller;

    @BeforeEach
    void setUp() {
        controller = new ArtistFollowController(fanJoinService, environment);
        // JWT 분기는 isLocalProfile()을 호출하지 않으므로 lenient 처리
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("POST /artists/{artistId}/follow")
    class FollowTest {

        @Test
        @DisplayName("FAN JWT → 201, FanJoinResult body 반환")
        void fanJwt_returns201() {
            Authentication auth = mockAuth("77", "ROLE_FAN");
            FanJoinResult result = new FanJoinResult(10L, 77L,
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));
            when(fanJoinService.join(eq(10L), eq(77L))).thenReturn(result);

            ResponseEntity<?> response = controller.follow(10L, auth, null);

            assertEquals(201, response.getStatusCode().value());
            verify(fanJoinService).join(eq(10L), eq(77L));
        }

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 헤더 fanId로 join 호출")
        void localProfile_headerFanId() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            FanJoinResult result = new FanJoinResult(10L, 55L,
                    OffsetDateTime.of(2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC));
            when(fanJoinService.join(eq(10L), eq(55L))).thenReturn(result);

            ResponseEntity<?> response = controller.follow(10L, null, 55L);

            assertEquals(201, response.getStatusCode().value());
            verify(fanJoinService).join(eq(10L), eq(55L));
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException")
        void noAuth_throws() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.follow(10L, null, null));
            verify(fanJoinService, never()).join(any(), any());
        }

        @Test
        @DisplayName("AGENCY JWT (비로컬) → ForbiddenException, join 미호출")
        void agencyJwt_forbidden() {
            Authentication auth = mockAuth("5", "ROLE_AGENCY");

            assertThrows(ForbiddenException.class,
                    () -> controller.follow(10L, auth, null));
            verify(fanJoinService, never()).join(any(), any());
        }

        @Test
        @DisplayName("ARTIST JWT (비로컬) → ForbiddenException, join 미호출")
        void artistJwt_forbidden() {
            Authentication auth = mockAuth("5", "ROLE_ARTIST");

            assertThrows(ForbiddenException.class,
                    () -> controller.follow(10L, auth, null));
            verify(fanJoinService, never()).join(any(), any());
        }

        @Test
        @DisplayName("이미 팬 가입 → AlreadyJoinedException 전파")
        void alreadyJoined_propagates() {
            Authentication auth = mockAuth("77", "ROLE_FAN");
            when(fanJoinService.join(eq(10L), eq(77L)))
                    .thenThrow(new AlreadyJoinedException("이미 팬 가입한 아티스트입니다."));

            assertThrows(AlreadyJoinedException.class,
                    () -> controller.follow(10L, auth, null));
        }
    }

    @Nested
    @DisplayName("DELETE /artists/{artistId}/follow")
    class UnfollowTest {

        @Test
        @DisplayName("FAN JWT → 204 No Content")
        void fanJwt_returns204() {
            Authentication auth = mockAuth("77", "ROLE_FAN");

            ResponseEntity<Void> response = controller.unfollow(10L, auth, null);

            assertEquals(204, response.getStatusCode().value());
            verify(fanJoinService).leave(eq(10L), eq(77L));
        }

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 헤더 fanId로 leave 호출")
        void localProfile_headerFanId() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});

            ResponseEntity<Void> response = controller.unfollow(10L, null, 55L);

            assertEquals(204, response.getStatusCode().value());
            verify(fanJoinService).leave(eq(10L), eq(55L));
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException")
        void noAuth_throws() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.unfollow(10L, null, null));
            verify(fanJoinService, never()).leave(any(), any());
        }

        @Test
        @DisplayName("AGENCY JWT (비로컬) → ForbiddenException, leave 미호출")
        void agencyJwt_forbidden() {
            Authentication auth = mockAuth("5", "ROLE_AGENCY");

            assertThrows(ForbiddenException.class,
                    () -> controller.unfollow(10L, auth, null));
            verify(fanJoinService, never()).leave(any(), any());
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