package com.fandrops.community.api.feed;

import com.fandrops.community.application.feed.FeedListResult;
import com.fandrops.community.application.feed.FeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FeedController.getFeeds — JWT viewer 분기 테스트.
 *
 * @Disabled 이유: JWT 필터 미구현 (표지민 담당). 완료 후 authority 접두사("ROLE_" 여부)
 *           확인하여 hasArtistOrAgencyRole / hasFanRole 문자열 맞춰 활성화.
 */
@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock FeedService feedService;
    @Mock Environment environment;

    FeedController feedController;

    @BeforeEach
    void setUp() {
        feedController = new FeedController(feedService, environment);
        // 비로컬 프로필 → 헤더 무시, JWT 분기 진입
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("getFeeds — JWT viewer 분기")
    class JwtViewerBranchTest {

        @Test
        @Disabled("JWT 필터 미구현 (표지민 담당) — 완료 후 authority 접두사(ROLE_ 여부) 확인 후 활성화")
        @DisplayName("ARTIST role JWT → viewerArtistMemberId로 FeedService 호출")
        void artistJwt_usesViewerArtistMemberId() {
            Authentication auth = mockAuth("5", "ARTIST");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(5L))).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(5L));
        }

        @Test
        @Disabled("JWT 필터 미구현 (표지민 담당) — 완료 후 authority 접두사(ROLE_ 여부) 확인 후 활성화")
        @DisplayName("AGENCY role JWT → viewerArtistMemberId로 FeedService 호출")
        void agencyJwt_usesViewerArtistMemberId() {
            Authentication auth = mockAuth("7", "AGENCY");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(7L))).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(7L));
        }

        @Test
        @Disabled("JWT 필터 미구현 (표지민 담당) — 완료 후 authority 접두사(ROLE_ 여부) 확인 후 활성화")
        @DisplayName("FAN role JWT → viewerFanId로 FeedService 호출")
        void fanJwt_usesViewerFanId() {
            Authentication auth = mockAuth("99", "FAN");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), eq(99L), isNull())).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), eq(99L), isNull());
        }

        @Test
        @Disabled("JWT 필터 미구현 (표지민 담당) — 완료 후 authority 접두사(ROLE_ 여부) 확인 후 활성화")
        @DisplayName("알 수 없는 role JWT → IllegalStateException")
        void unknownRoleJwt_throwsIllegalState() {
            Authentication auth = mockAuth("1", "UNKNOWN_ROLE");

            assertThrows(IllegalStateException.class,
                    () -> feedController.getFeeds(10L, null, 20, auth, null, null));
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