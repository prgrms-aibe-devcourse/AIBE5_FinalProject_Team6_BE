package com.fandrops.community.application.vote;

import com.fandrops.community.application.exception.DuplicateVoteException;
import com.fandrops.community.application.exception.GoodsVoteClosedException;
import com.fandrops.community.application.exception.GoodsVoteNotFoundException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.vote.GoodsVote;
import com.fandrops.community.domain.vote.GoodsVoteOption;
import com.fandrops.community.domain.vote.GoodsVoteRecord;
import com.fandrops.community.domain.vote.exception.GoodsVoteDomainException;
import com.fandrops.community.domain.vote.repository.GoodsVoteOptionRepository;
import com.fandrops.community.domain.vote.repository.GoodsVoteRecordRepository;
import com.fandrops.community.domain.vote.repository.GoodsVoteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GoodsVoteService {

    private final GoodsVoteRepository voteRepository;
    private final GoodsVoteOptionRepository optionRepository;
    private final GoodsVoteRecordRepository recordRepository;
    private final FanMembershipPort fanMembershipPort;
    private final Clock clock;

    public GoodsVoteService(GoodsVoteRepository voteRepository,
                            GoodsVoteOptionRepository optionRepository,
                            GoodsVoteRecordRepository recordRepository,
                            FanMembershipPort fanMembershipPort,
                            Clock clock) {
        this.voteRepository = voteRepository;
        this.optionRepository = optionRepository;
        this.recordRepository = recordRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.clock = clock;
    }

    @Transactional
    public Long createVote(GoodsVoteCreateCommand command) {
        Objects.requireNonNull(command.artistId(), "artistId is required");
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime endsAtUtc = command.endsAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();

        GoodsVote vote = GoodsVote.create(command.artistId(), command.title(), endsAtUtc, now);
        GoodsVote saved = voteRepository.save(vote);

        for (GoodsVoteCreateCommand.OptionInput opt : command.options()) {
            optionRepository.save(GoodsVoteOption.create(saved.getId(), opt.label(), opt.imageUrl()));
        }
        return saved.getId();
    }

    public List<GoodsVoteResult> getVotes(Long artistId, Long cursorId, int size) {
        List<GoodsVote> votes = voteRepository.findByArtistId(artistId, cursorId, size + 1);
        if (votes.isEmpty()) {
            return List.of();
        }
        List<Long> voteIds = votes.stream().map(GoodsVote::getId).toList();
        Map<Long, List<GoodsVoteOptionResult>> optionsByVoteId = optionRepository.findByVoteIdIn(voteIds)
                .stream()
                .collect(Collectors.groupingBy(
                        GoodsVoteOption::getVoteId,
                        Collectors.mapping(
                                o -> new GoodsVoteOptionResult(o.getId(), o.getLabel(), o.getImageUrl(), o.getVoteCount()),
                                Collectors.toList())));
        return votes.stream()
                .map(vote -> GoodsVoteResult.of(vote, optionsByVoteId.getOrDefault(vote.getId(), List.of())))
                .toList();
    }

    @Transactional
    public GoodsBallotResult castBallot(GoodsBallotCommand command) {
        // now를 한 번만 캡처 — isVotable 체크와 레코드 저장에 동일 시각 사용 (TOCTOU 방지)
        LocalDateTime now = LocalDateTime.now(clock);

        // 1. 투표 조회 및 유효성 검증 (artistId는 vote에서 가져옴)
        GoodsVote vote = voteRepository.findById(command.voteId())
                .orElseThrow(() -> new GoodsVoteNotFoundException("투표를 찾을 수 없습니다."));

        if (!vote.isVotable(now)) {
            throw new GoodsVoteClosedException("마감되었거나 비활성화된 투표입니다.");
        }

        // 2. 팬 가입 여부 확인 (USER_FOLLOW 조회)
        if (!fanMembershipPort.isFanOf(command.fanId(), vote.getArtistId())) {
            throw new NotFanMemberException("팬 가입 후 투표할 수 있습니다.");
        }

        // 3. 선택지 유효성 검증
        GoodsVoteOption option = optionRepository.findById(command.optionId())
                .orElseThrow(() -> new GoodsVoteNotFoundException("선택지를 찾을 수 없습니다."));

        if (!option.getVoteId().equals(command.voteId())) {
            throw new GoodsVoteDomainException("선택지가 해당 투표에 속하지 않습니다.");
        }

        // 4. 투표 기록 저장 — try 범위를 save()만 감쌈 (incrementVoteCount 오류 오분류 방지)
        GoodsVoteRecord record;
        try {
            record = recordRepository.save(
                    GoodsVoteRecord.create(command.voteId(), command.optionId(), command.fanId(), now));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVoteException("이미 투표하셨습니다.");
        }

        // 5. vote_count 원자적 증가
        optionRepository.incrementVoteCount(command.optionId());
        return new GoodsBallotResult(record.getId());
    }
}
