package com.fandrops.community.application.follow;

import com.fandrops.community.application.exception.AlreadyJoinedException;
import com.fandrops.community.application.exception.ArtistNotFoundException;
import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FanJoinServiceTest {

    @Mock UserFollowRepository userFollowRepository;
    @Mock FanMembershipPort fanMembershipPort;
    @Mock ArtistProfilePort artistProfilePort;

    FanJoinService fanJoinService;
    Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
        fanJoinService = new FanJoinService(userFollowRepository, fanMembershipPort, artistProfilePort, clock);
    }

    @Nested
    @DisplayName("join")
    class JoinTest {

        @Test
        @DisplayName("팬 가입 성공 — UserFollow 저장 + fanCount 증가 + FanJoinResult 반환")
        void success() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);
            UserFollow saved = UserFollow.reconstruct(1L, 77L, 10L,
                    LocalDateTime.of(2026, 6, 1, 0, 0, 0));
            when(userFollowRepository.save(any(UserFollow.class))).thenReturn(saved);

            FanJoinResult result = fanJoinService.join(10L, 77L);

            assertEquals(10L, result.artistId());
            assertEquals(77L, result.fanId());
            assertEquals(
                    LocalDateTime.of(2026, 6, 1, 0, 0, 0).atOffset(ZoneOffset.UTC),
                    result.followedAt());
            verify(artistProfilePort).incrementFanCount(eq(10L));
        }

        @Test
        @DisplayName("존재하지 않는 아티스트 → ArtistNotFoundException, 이후 단계 미호출")
        void artistNotFound_throws() {
            when(artistProfilePort.exists(eq(9999L))).thenReturn(false);

            assertThrows(ArtistNotFoundException.class, () -> fanJoinService.join(9999L, 77L));
            verifyNoInteractions(fanMembershipPort);
            verify(userFollowRepository, never()).save(any());
        }

        @Test
        @DisplayName("저장 시 clock 기준 followedAt이 UserFollow에 주입됨")
        void save_followedAt_usesFixedClock() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);
            ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
            UserFollow saved = UserFollow.reconstruct(1L, 77L, 10L,
                    LocalDateTime.of(2026, 6, 1, 0, 0, 0));
            when(userFollowRepository.save(captor.capture())).thenReturn(saved);

            fanJoinService.join(10L, 77L);

            assertEquals(LocalDateTime.of(2026, 6, 1, 0, 0, 0), captor.getValue().getFollowedAt());
        }

        @Test
        @DisplayName("이미 팬 가입 → AlreadyJoinedException, save 호출 없음")
        void alreadyJoined_throws() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(true);

            assertThrows(AlreadyJoinedException.class, () -> fanJoinService.join(10L, 77L));
            verify(userFollowRepository, never()).save(any());
            verify(artistProfilePort, never()).incrementFanCount(any());
        }

        @Test
        @DisplayName("동시 요청으로 UK 충돌(DataIntegrityViolationException) → AlreadyJoinedException으로 변환")
        void concurrentJoin_dataIntegrityViolation_throwsAlreadyJoined() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);
            when(userFollowRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("uq_user_follow violated"));

            assertThrows(AlreadyJoinedException.class, () -> fanJoinService.join(10L, 77L));
            verify(artistProfilePort, never()).incrementFanCount(any());
        }

        @Test
        @DisplayName("save 성공 후 incrementFanCount가 DataIntegrityViolationException을 던지면 그대로 전파 — AlreadyJoinedException 오변환 없음")
        void incrementFanCount_dive_propagatesAsIs() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);
            when(userFollowRepository.save(any())).thenReturn(
                    UserFollow.reconstruct(1L, 77L, 10L, LocalDateTime.of(2026, 6, 1, 0, 0, 0)));
            doThrow(new DataIntegrityViolationException("artist_profile constraint"))
                    .when(artistProfilePort).incrementFanCount(eq(10L));

            assertThrows(DataIntegrityViolationException.class, () -> fanJoinService.join(10L, 77L));
        }

        @Test
        @DisplayName("가입 성공 시 fanCount 증가는 정확히 1회 호출됨")
        void incrementFanCount_calledOnce() {
            when(artistProfilePort.exists(eq(10L))).thenReturn(true);
            when(fanMembershipPort.isFanOf(eq(77L), eq(10L))).thenReturn(false);
            when(userFollowRepository.save(any())).thenReturn(
                    UserFollow.reconstruct(1L, 77L, 10L, LocalDateTime.now(clock)));

            fanJoinService.join(10L, 77L);

            verify(artistProfilePort, times(1)).incrementFanCount(eq(10L));
        }
    }

    @Nested
    @DisplayName("leave")
    class LeaveTest {

        @Test
        @DisplayName("팔로우 해지 성공 — 1행 삭제 시 decrementFanCount 호출")
        void success() {
            when(userFollowRepository.deleteByFanIdAndArtistId(eq(77L), eq(10L))).thenReturn(1);

            fanJoinService.leave(10L, 77L);

            verify(artistProfilePort).decrementFanCount(eq(10L));
        }

        @Test
        @DisplayName("미가입 상태에서 leave 호출 — 0행 삭제 시 decrementFanCount 미호출 (멱등)")
        void notJoined_idempotent_noDecrement() {
            when(userFollowRepository.deleteByFanIdAndArtistId(eq(99L), eq(10L))).thenReturn(0);

            assertDoesNotThrow(() -> fanJoinService.leave(10L, 99L));
            verify(artistProfilePort, never()).decrementFanCount(any());
        }
    }
}