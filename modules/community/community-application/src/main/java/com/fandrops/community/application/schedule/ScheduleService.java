package com.fandrops.community.application.schedule;

import com.fandrops.community.domain.schedule.ArtistSchedule;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.repository.ArtistScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final ArtistScheduleRepository scheduleRepository;

    public ScheduleService(ArtistScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // TODO: ArtistMembershipPort 구현 후 command.artistMemberId()와 command.artistId() 소속 검증 추가
    @Transactional
    public ScheduleResult createEvent(EventCreateCommand command) {
        Objects.requireNonNull(command.artistMemberId(), "artistMemberId is required");
        ArtistScheduleType type;
        try {
            type = ArtistScheduleType.valueOf(command.type());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 일정 유형입니다: " + command.type() + " (DROP|LIVE|EVENT|NOTICE)");
        }
        LocalDateTime scheduledAtUtc = command.scheduledAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        ArtistSchedule schedule = ArtistSchedule.create(
                command.artistId(), command.title(), type, scheduledAtUtc);
        return toResult(scheduleRepository.save(schedule));
    }

    public List<ScheduleResult> getCalendar(Long artistId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = (from != null) ? from : LocalDateTime.now().minusDays(30);
        LocalDateTime effectiveTo = (to != null) ? to : LocalDateTime.now().plusDays(90);
        return scheduleRepository.findByArtistIdBetween(artistId, effectiveFrom, effectiveTo)
                .stream()
                .map(this::toResult)
                .toList();
    }

    private ScheduleResult toResult(ArtistSchedule s) {
        return new ScheduleResult(
                s.getId(),
                s.getType(),
                s.getTitle(),
                s.getScheduledAt().atOffset(ZoneOffset.UTC)
        );
    }
}