package com.fandrops.community.application.attendance;

import com.fandrops.community.application.exception.AlreadyCheckedInException;
import com.fandrops.community.application.exception.AttendanceEventNotFoundException;
import com.fandrops.community.application.exception.AttendanceEventNotOngoingException;
import com.fandrops.community.application.exception.NotFanMemberException;
import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.domain.attendance.AttendanceEvent;
import com.fandrops.community.domain.attendance.AttendanceLog;
import com.fandrops.community.domain.attendance.exception.DuplicateAttendanceException;
import com.fandrops.community.domain.attendance.repository.AttendanceEventRepository;
import com.fandrops.community.domain.attendance.repository.AttendanceLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceEventRepository eventRepository;
    private final AttendanceLogRepository logRepository;
    private final FanMembershipPort fanMembershipPort;
    private final Clock clock;

    public AttendanceService(AttendanceEventRepository eventRepository,
                             AttendanceLogRepository logRepository,
                             FanMembershipPort fanMembershipPort,
                             Clock clock) {
        this.eventRepository = eventRepository;
        this.logRepository = logRepository;
        this.fanMembershipPort = fanMembershipPort;
        this.clock = clock;
    }

    public List<AttendanceEventResult> getActiveEvents(Long artistId) {
        LocalDate today = LocalDate.now(clock);
        return eventRepository.findActiveByArtistIdAndDate(artistId, today).stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional
    public CheckInResult checkIn(Long eventId, Long fanId) {
        AttendanceEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new AttendanceEventNotFoundException("출석 이벤트를 찾을 수 없습니다."));

        LocalDate today = LocalDate.now(clock);
        if (!event.isOngoing(today)) {
            throw new AttendanceEventNotOngoingException("진행 중인 출석 이벤트가 아닙니다.");
        }

        if (!fanMembershipPort.isFanOf(fanId, event.getArtistId())) {
            throw new NotFanMemberException("팬 가입 후 출석 체크를 할 수 있습니다.");
        }

        if (logRepository.existsByEventIdAndFanIdAndCheckedDate(eventId, fanId, today)) {
            throw new AlreadyCheckedInException("오늘 이미 출석 체크를 완료했습니다.");
        }

        try {
            logRepository.save(AttendanceLog.create(eventId, fanId, clock));
        } catch (DuplicateAttendanceException e) {
            // saveAndFlush flush 이후 UNIQUE 제약 위반 (동시 요청 경합)
            throw new AlreadyCheckedInException(e.getMessage());
        }
        // 연속 일수가 아닌 총 체크인 횟수 (팀 합의: 총 7회 달성 시 리워드)
        // countByEventIdAndFanId() = 해당 이벤트에서 팬의 총 체크인 수
        int streakDays = logRepository.countByEventIdAndFanId(eventId, fanId);
        return new CheckInResult(eventId, today, streakDays);
    }

    private AttendanceEventResult toResult(AttendanceEvent e) {
        return new AttendanceEventResult(e.getId(), e.getStartDate(), e.getEndDate(), e.getRewardDesc());
    }
}