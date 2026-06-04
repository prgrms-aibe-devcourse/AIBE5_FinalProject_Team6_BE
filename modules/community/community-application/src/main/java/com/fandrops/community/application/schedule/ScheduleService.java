package com.fandrops.community.application.schedule;

import com.fandrops.community.domain.schedule.ArtistSchedule;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.repository.ArtistScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
        if (command.artistMemberId() == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
        }
        ArtistScheduleType type;
        try {
            type = ArtistScheduleType.valueOf(command.type());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 일정 유형입니다: " + command.type() + " (DROP|LIVE|EVENT|NOTICE)");
        }
        ArtistSchedule schedule = ArtistSchedule.create(
                command.artistId(), command.title(), type, command.scheduledAt());
        return toResult(scheduleRepository.save(schedule));
    }

    public List<ScheduleResult> getCalendar(Long artistId, LocalDateTime from, LocalDateTime to) {
        return scheduleRepository.findByArtistIdBetween(artistId, from, to)
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