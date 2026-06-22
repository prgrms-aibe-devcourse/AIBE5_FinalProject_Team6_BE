package com.fandrops.community.api.feed;

import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.feed.FeedListResult;
import com.fandrops.community.application.feed.FeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.mockito.Mockito.lenient;
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
 */
@ExtendWith(MockitoExtension.class)
class FeedControllerTest {

    @Mock FeedService feedService;
    @Mock Environment environment;

    FeedController feedController;

    @BeforeEach
    void setUp() {
        feedController = new FeedController(feedService, environment);
        // 비로컬 프로필 → 헤더 무시, JWT 분기 진입. lenient: header=null 시 단락 평가로 isLocalProfile() 미호출
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("getFeeds — JWT viewer 분기")
    class JwtViewerBranchTest {

        @Test
        @DisplayName("ARTIST role JWT → viewerArtistMemberId로 FeedService 호출")
        void artistJwt_usesViewerArtistMemberId() {
            Authentication auth = mockAuth("5", "ROLE_ARTIST");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(5L))).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(5L));
        }

        @Test
        @DisplayName("AGENCY role JWT → viewerArtistMemberId로 FeedService 호출")
        void agencyJwt_usesViewerArtistMemberId() {
            Authentication auth = mockAuth("7", "ROLE_AGENCY");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(7L))).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), isNull(), eq(7L));
        }

        @Test
        @DisplayName("FAN role JWT → viewerFanId로 FeedService 호출")
        void fanJwt_usesViewerFanId() {
            Authentication auth = mockAuth("99", "ROLE_FAN");
            FeedListResult stub = new FeedListResult(List.of(), null, false);
            when(feedService.getFeeds(eq(10L), isNull(), eq(20), eq(99L), isNull())).thenReturn(stub);

            assertDoesNotThrow(() -> feedController.getFeeds(10L, null, 20, auth, null, null));

            verify(feedService).getFeeds(eq(10L), isNull(), eq(20), eq(99L), isNull());
        }

        @Test
        @DisplayName("알 수 없는 role JWT → ForbiddenException (403)")
        void unknownRoleJwt_throwsForbidden() {
            Authentication auth = mockAuth("1", "UNKNOWN_ROLE");

            assertThrows(ForbiddenException.class,
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