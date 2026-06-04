package com.fandrops.community.application.schedule;

import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.port.ScheduleNotificationPort;
import com.fandrops.community.domain.schedule.ArtistSchedule;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.exception.ScheduleDomainException;
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
    private final ScheduleNotificationPort notificationPort;

    public ScheduleService(ArtistScheduleRepository scheduleRepository,
                           ScheduleNotificationPort notificationPort) {
        this.scheduleRepository = scheduleRepository;
        this.notificationPort = notificationPort;
    }

    @Transactional
    public ScheduleResult createEvent(EventCreateCommand command) {
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

    // artistMemberId: 인증된 아티스트 멤버 식별자. 소속 검증(ArtistMembershipPort)은 미구현 — 구현 후 schedule.getArtistId()와 대조
    @Transactional
    public LiveStartResult startLive(Long liveId, Long artistMemberId) {
        if (artistMemberId == null) {
            throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
        }
        ArtistSchedule schedule = scheduleRepository.findById(liveId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다."));
        try {
            schedule.startLive();
        } catch (ScheduleDomainException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        scheduleRepository.save(schedule);
        notificationPort.notifyLiveStarted(schedule.getArtistId(), schedule.getId(), schedule.getTitle());
        return new LiveStartResult(schedule.getId(), schedule.isLive());
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