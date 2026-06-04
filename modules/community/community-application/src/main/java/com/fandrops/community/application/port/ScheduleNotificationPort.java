package com.fandrops.community.application.port;

public interface ScheduleNotificationPort {

    // PATCH /lives/{id}/start 성공 시 팬 알림 발행 — 전송은 notification 모듈 담당 (mvp-api-spec.md §Artist/Event)
    void notifyLiveStarted(Long artistId, Long scheduleId, String title);
}