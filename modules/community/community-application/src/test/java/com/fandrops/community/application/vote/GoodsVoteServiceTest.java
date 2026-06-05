package com.fandrops.community.application.vote;

import com.fandrops.community.application.exception.DuplicateVoteException;
import com.fandrops.community.application.exception.GoodsVoteClosedException;
import com.fandrops.community.application.exception.GoodsVoteNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.vote.GoodsVote;
import com.fandrops.community.domain.vote.GoodsVoteOption;
import com.fandrops.community.domain.vote.GoodsVoteRecord;
import com.fandrops.community.domain.vote.repository.GoodsVoteOptionRepository;
import com.fandrops.community.domain.vote.repository.GoodsVoteRecordRepository;
import com.fandrops.community.domain.vote.repository.GoodsVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoodsVoteServiceTest {

    @Mock GoodsVoteRepository voteRepository;
    @Mock GoodsVoteOptionRepository optionRepository;
    @Mock GoodsVoteRecordRepository recordRepository;
    @Mock FanMembershipPort fanMembershipPort;

    GoodsVoteService service;
    Clock clock;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 12, 0);

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
        service = new GoodsVoteService(voteRepository, optionRepository, recordRepository, fanMembershipPort, clock);
    }

    @Nested
    @DisplayName("getVotes")
    class GetVotesTest {

        @Test
        @DisplayName("findByVoteIdIn 단일 쿼리로 옵션 일괄 조회 — N+1 없음")
        void batchLoadOptions() {
            GoodsVote v1 = vote(1L, true, NOW.plusDays(7));
            GoodsVote v2 = vote(2L, true, NOW.plusDays(14));
            GoodsVoteOption opt1 = GoodsVoteOption.reconstruct(10L, 1L, "A", null, 3);
            GoodsVoteOption opt2 = GoodsVoteOption.reconstruct(11L, 2L, "B", null, 1);

            when(voteRepository.findByArtistId(100L, null, 20)).thenReturn(List.of(v1, v2));
            when(optionRepository.findByVoteIdIn(anyList())).thenReturn(List.of(opt1, opt2));

            List<GoodsVoteResult> results = service.getVotes(100L, null, 20);

            assertEquals(2, results.size());
            assertEquals(1, results.get(0).options().size());
            assertEquals(1, results.get(1).options().size());
            verify(optionRepository, times(1)).findByVoteIdIn(anyList());
            verify(optionRepository, never()).findByVoteId(any());
        }

        @Test
        @DisplayName("투표 없을 때 빈 리스트 반환 — findByVoteIdIn 호출 없음")
        void emptyVotes_returnsEmpty() {
            when(voteRepository.findByArtistId(100L, null, 20)).thenReturn(List.of());

            List<GoodsVoteResult> results = service.getVotes(100L, null, 20);

            assertTrue(results.isEmpty());
            verify(optionRepository, never()).findByVoteIdIn(anyList());
        }
    }

    @Nested
    @DisplayName("createVote")
    class CreateVoteTest {

        @Test
        @DisplayName("유효한 커맨드 — vote + options 저장 후 voteId 반환")
        void success() {
            GoodsVote saved = vote(1L, true, NOW.plusDays(7));
            when(voteRepository.save(any())).thenReturn(saved);
            GoodsVoteOption opt = GoodsVoteOption.reconstruct(10L, 1L, "A", null, 0);
            when(optionRepository.save(any())).thenReturn(opt);

            Long voteId = service.createVote(new GoodsVoteCreateCommand(
                    1L, "투표", NOW.plusDays(7).atOffset(ZoneOffset.UTC),
                    List.of(new GoodsVoteCreateCommand.OptionInput("A", null))));

            assertEquals(1L, voteId);
            verify(voteRepository).save(any());
            verify(optionRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("castBallot")
    class CastBallotTest {

        @Test
        @DisplayName("정상 투표 — recordId 반환, vote_count 증가")
        void success() {
            GoodsVote openVote = vote(1L, true, NOW.plusDays(7));
            GoodsVoteOption opt = GoodsVoteOption.reconstruct(10L, 1L, "A", null, 0);
            GoodsVoteRecord record = GoodsVoteRecord.reconstruct(99L, 1L, 10L, 5L, NOW);

            when(voteRepository.findById(1L)).thenReturn(Optional.of(openVote));
            when(fanMembershipPort.isFanOf(5L, openVote.getArtistId())).thenReturn(true);
            when(optionRepository.findById(10L)).thenReturn(Optional.of(opt));
            when(recordRepository.save(any())).thenReturn(record);

            GoodsBallotResult result = service.castBallot(new GoodsBallotCommand(1L, 10L, 5L));

            assertEquals(99L, result.recordId());
            verify(optionRepository).incrementVoteCount(10L);
        }

        @Test
        @DisplayName("투표 없음 → GoodsVoteNotFoundException")
        void voteNotFound_throws() {
            when(voteRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(GoodsVoteNotFoundException.class,
                    () -> service.castBallot(new GoodsBallotCommand(99L, 10L, 5L)));
            verify(recordRepository, never()).save(any());
        }

        @Test
        @DisplayName("마감된 투표 → GoodsVoteClosedException")
        void closedVote_throws() {
            GoodsVote closed = vote(1L, true, NOW.minusDays(1));
            when(voteRepository.findById(1L)).thenReturn(Optional.of(closed));

            assertThrows(GoodsVoteClosedException.class,
                    () -> service.castBallot(new GoodsBallotCommand(1L, 10L, 5L)));
        }

        @Test
        @DisplayName("팬 미가입 → NotFanMemberException")
        void notFanMember_throws() {
            GoodsVote openVote = vote(1L, true, NOW.plusDays(7));
            when(voteRepository.findById(1L)).thenReturn(Optional.of(openVote));
            when(fanMembershipPort.isFanOf(5L, openVote.getArtistId())).thenReturn(false);

            assertThrows(NotFanMemberException.class,
                    () -> service.castBallot(new GoodsBallotCommand(1L, 10L, 5L)));
        }

        @Test
        @DisplayName("중복 투표 (UK 위반) → DuplicateVoteException")
        void duplicateVote_throws() {
            GoodsVote openVote = vote(1L, true, NOW.plusDays(7));
            GoodsVoteOption opt = GoodsVoteOption.reconstruct(10L, 1L, "A", null, 0);

            when(voteRepository.findById(1L)).thenReturn(Optional.of(openVote));
            when(fanMembershipPort.isFanOf(5L, openVote.getArtistId())).thenReturn(true);
            when(optionRepository.findById(10L)).thenReturn(Optional.of(opt));
            when(recordRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

            assertThrows(DuplicateVoteException.class,
                    () -> service.castBallot(new GoodsBallotCommand(1L, 10L, 5L)));
            verify(optionRepository, never()).incrementVoteCount(any());
        }

        @Test
        @DisplayName("선택지 없음 → GoodsVoteNotFoundException")
        void optionNotFound_throws() {
            GoodsVote openVote = vote(1L, true, NOW.plusDays(7));
            when(voteRepository.findById(1L)).thenReturn(Optional.of(openVote));
            when(fanMembershipPort.isFanOf(5L, openVote.getArtistId())).thenReturn(true);
            when(optionRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(GoodsVoteNotFoundException.class,
                    () -> service.castBallot(new GoodsBallotCommand(1L, 99L, 5L)));
            verify(recordRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 투표의 선택지 → IllegalArgumentException")
        void wrongOption_throws() {
            GoodsVote openVote = vote(1L, true, NOW.plusDays(7));
            // option이 voteId=2에 속함
            GoodsVoteOption wrongOpt = GoodsVoteOption.reconstruct(10L, 2L, "A", null, 0);

            when(voteRepository.findById(1L)).thenReturn(Optional.of(openVote));
            when(fanMembershipPort.isFanOf(5L, openVote.getArtistId())).thenReturn(true);
            when(optionRepository.findById(10L)).thenReturn(Optional.of(wrongOpt));

            assertThrows(IllegalArgumentException.class,
                    () -> service.castBallot(new GoodsBallotCommand(1L, 10L, 5L)));
        }
    }

    private static GoodsVote vote(Long id, boolean active, LocalDateTime endsAt) {
        return GoodsVote.reconstruct(id, 100L, "테스트 투표", endsAt, active, NOW.minusDays(1));
    }
}
