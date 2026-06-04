package com.fandrops.community.infrastructure.schedule;

import com.fandrops.community.application.port.ScheduleNotificationPort;
import org.springframework.stereotype.Component;

// notification 모듈 구현 전 no-op — 표지민님 notification 완료 후 실 구현체로 교체
@Component
public class NoOpScheduleNotificationAdapter implements ScheduleNotificationPort {

    @Override
    public void notifyLiveStarted(Long artistId, Long scheduleId, String title) {
        // no-op
    }
}