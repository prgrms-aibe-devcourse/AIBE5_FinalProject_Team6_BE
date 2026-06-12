package com.fandrops.community.application.schedule;

import com.fandrops.community.application.event.ArtistScheduleEvent;
import com.fandrops.community.application.exception.ScheduleNotFoundException;
import com.fandrops.community.application.port.OutboxEvent;
import com.fandrops.community.application.port.OutboxEventPort;
import com.fandrops.community.application.port.OutboxEventType;
import com.fandrops.community.domain.schedule.ArtistSchedule;
import org.springframework.context.ApplicationEventPublisher;
import com.fandrops.community.domain.schedule.ArtistScheduleType;
import com.fandrops.community.domain.schedule.repository.ArtistScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ScheduleService {

    private final ArtistScheduleRepository scheduleRepository;
    private final OutboxEventPort outboxEventPort;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ScheduleService(ArtistScheduleRepository scheduleRepository,
                           OutboxEventPort outboxEventPort,
                           ApplicationEventPublisher applicationEventPublisher) {
        this.scheduleRepository = scheduleRepository;
        this.outboxEventPort = outboxEventPort;
        this.applicationEventPublisher = applicationEventPublisher;
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

    // TODO: ArtistMembershipPort 구현 후 artistMemberId가 schedule.artistId() 소속인지 검증 추가
    @Transactional
    public ScheduleResult startLive(Long scheduleId, Long artistMemberId) {
        ArtistSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ScheduleNotFoundException("일정을 찾을 수 없습니다."));
        if (schedule.getType() != ArtistScheduleType.LIVE) {
            throw new IllegalArgumentException("LIVE 타입 일정만 시작할 수 있습니다.");
        }
        if (outboxEventPort.existsEvent(schedule.getId(), OutboxEventType.LIVE_START)) {
            return toResult(schedule);
        }
        outboxEventPort.publish(new OutboxEvent(
                OutboxEventType.LIVE_START, schedule.getId(),
                Map.of("scheduleId", schedule.getId(),
                       "artistId", schedule.getArtistId(),
                       "artistMemberId", artistMemberId,
                       "type", schedule.getType().name(),
                       "title", schedule.getTitle())
        ));
        applicationEventPublisher.publishEvent(new ArtistScheduleEvent(schedule.getId(), schedule.getArtistId()));
        return toResult(schedule);
    }

    @Transactional
    public ScheduleResult registerLive(LiveCreateCommand command) {
        Objects.requireNonNull(command.artistMemberId(), "artistMemberId is required");
        LocalDateTime scheduledAtUtc = command.scheduledAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        ArtistSchedule schedule = ArtistSchedule.createLive(
                command.artistId(), command.title(), scheduledAtUtc, command.liveUrl());
        return toResult(scheduleRepository.save(schedule));
    }

    public List<ScheduleResult> getLives(Long artistId) {
        return scheduleRepository.findLivesByArtistId(artistId)
                .stream()
                .map(this::toResult)
                .toList();
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
                s.getScheduledAt().atOffset(ZoneOffset.UTC),
                s.getLiveUrl()
        );
    }
}