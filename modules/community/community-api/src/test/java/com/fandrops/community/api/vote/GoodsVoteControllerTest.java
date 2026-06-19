package com.fandrops.community.api.vote;

import com.fandrops.community.application.exception.DuplicateVoteException;
import com.fandrops.community.application.exception.ForbiddenException;
import com.fandrops.community.application.exception.UnauthorizedException;
import com.fandrops.community.application.vote.GoodsBallotResult;
import com.fandrops.community.application.vote.GoodsVoteResult;
import com.fandrops.community.application.vote.GoodsVoteService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoodsVoteControllerTest {

    @Mock GoodsVoteService goodsVoteService;
    @Mock Environment environment;

    GoodsVoteController controller;

    @BeforeEach
    void setUp() {
        controller = new GoodsVoteController(goodsVoteService, environment);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
    }

    @Nested
    @DisplayName("GET /api/v1/artists/{artistId}/goods-votes")
    class GetVotesTest {

        @Test
        @DisplayName("공개 조회 → 200, 목록 반환")
        void public_returnsVoteList() {
            when(goodsVoteService.getVotes(eq(10L), isNull(), eq(20))).thenReturn(List.of());

            ResponseEntity<?> response = controller.getVotes(10L, null, 20);

            assertEquals(200, response.getStatusCode().value());
            verify(goodsVoteService).getVotes(eq(10L), isNull(), eq(20));
        }

        @Test
        @DisplayName("size=0 (잘못된 입력) → IllegalArgumentException")
        void sizeZero_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.getVotes(10L, null, 0));
            verify(goodsVoteService, never()).getVotes(any(), any(), anyInt());
        }

        @Test
        @DisplayName("size=51 (잘못된 입력) → IllegalArgumentException")
        void sizeOver50_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.getVotes(10L, null, 51));
            verify(goodsVoteService, never()).getVotes(any(), any(), anyInt());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/artists/{artistId}/goods-votes")
    class CreateVoteTest {

        private GoodsVoteCreateRequest validRequest() {
            return new GoodsVoteCreateRequest(
                    "굿즈 투표 제목",
                    OffsetDateTime.of(2026, 12, 31, 23, 59, 0, 0, ZoneOffset.UTC),
                    List.of(new GoodsVoteCreateRequest.OptionInput("A안", "https://img.example.com/a.jpg"),
                            new GoodsVoteCreateRequest.OptionInput("B안", "https://img.example.com/b.jpg")));
        }

        @Test
        @DisplayName("로컬 프로필 → AGENCY 체크 우회, 201 반환")
        void localProfile_bypassesAgencyCheck_returns201() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(goodsVoteService.createVote(any())).thenReturn(100L);

            ResponseEntity<?> response = controller.createVote(10L, validRequest(), null, 5L);

            assertEquals(201, response.getStatusCode().value());
            verify(goodsVoteService).createVote(any());
        }

        @Test
        @DisplayName("AGENCY JWT (비로컬) → 201 반환")
        void agencyJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("5", "AGENCY");
            when(goodsVoteService.createVote(any())).thenReturn(101L);

            ResponseEntity<?> response = controller.createVote(10L, validRequest(), auth, null);

            assertEquals(201, response.getStatusCode().value());
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → ForbiddenException, service 미호출")
        void fanJwt_nonLocal_throwsForbidden() {
            Authentication auth = mockAuth("77", "FAN");

            assertThrows(ForbiddenException.class,
                    () -> controller.createVote(10L, validRequest(), auth, null));
            verify(goodsVoteService, never()).createVote(any());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → ForbiddenException, service 미호출")
        void noAuth_nonLocal_throwsForbidden() {
            assertThrows(ForbiddenException.class,
                    () -> controller.createVote(10L, validRequest(), null, null));
            verify(goodsVoteService, never()).createVote(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/goods-votes/{id}/ballots")
    class CastBallotTest {

        @Test
        @DisplayName("로컬 프로필 + X-Fan-Id 헤더 → 201, recordId 반환")
        void localProfile_fanHeader_returns201() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(goodsVoteService.castBallot(any())).thenReturn(new GoodsBallotResult(999L));

            ResponseEntity<?> response = controller.castBallot(
                    1L, new GoodsBallotRequest(2L), null, 55L);

            assertEquals(201, response.getStatusCode().value());
            verify(goodsVoteService).castBallot(any());
        }

        @Test
        @DisplayName("FAN JWT (비로컬) → 201 반환")
        void fanJwt_nonLocal_returns201() {
            Authentication auth = mockAuth("77", "FAN");
            when(goodsVoteService.castBallot(any())).thenReturn(new GoodsBallotResult(1000L));

            ResponseEntity<?> response = controller.castBallot(
                    1L, new GoodsBallotRequest(2L), auth, null);

            assertEquals(201, response.getStatusCode().value());
        }

        @Test
        @DisplayName("인증 없음 (비로컬) → UnauthorizedException, service 미호출")
        void noAuth_nonLocal_throwsUnauthorized() {
            assertThrows(UnauthorizedException.class,
                    () -> controller.castBallot(1L, new GoodsBallotRequest(2L), null, null));
            verify(goodsVoteService, never()).castBallot(any());
        }

        @Test
        @DisplayName("중복 투표 → DuplicateVoteException 전파")
        void duplicateVote_propagates() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
            when(goodsVoteService.castBallot(any()))
                    .thenThrow(new DuplicateVoteException("이미 투표하셨습니다."));

            assertThrows(DuplicateVoteException.class,
                    () -> controller.castBallot(1L, new GoodsBallotRequest(2L), null, 55L));
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