package com.fandrops.community.domain.schedule;

import com.fandrops.community.domain.schedule.exception.ScheduleDomainException;

import java.time.LocalDateTime;

public class ArtistSchedule {

    private final Long id;
    private final Long artistId;
    private final Long noticeId;
    private final String title;
    private final ArtistScheduleType type;
    private final LocalDateTime scheduledAt;
    private boolean isLive;

    private ArtistSchedule(Long id, Long artistId, Long noticeId, String title,
                           ArtistScheduleType type, LocalDateTime scheduledAt, boolean isLive) {
        this.id = id;
        this.artistId = artistId;
        this.noticeId = noticeId;
        this.title = title;
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.isLive = isLive;
    }

    public static ArtistSchedule create(Long artistId, String title,
                                        ArtistScheduleType type, LocalDateTime scheduledAt) {
        return new ArtistSchedule(null, artistId, null, title, type, scheduledAt, false);
    }

    public static ArtistSchedule reconstruct(Long id, Long artistId, Long noticeId, String title,
                                              ArtistScheduleType type, LocalDateTime scheduledAt,
                                              boolean isLive) {
        return new ArtistSchedule(id, artistId, noticeId, title, type, scheduledAt, isLive);
    }

    // LIVE 타입만 온에어 전환 가능 — ERD §8
    public void startLive() {
        if (type != ArtistScheduleType.LIVE) {
            throw new ScheduleDomainException("LIVE 타입 일정만 시작할 수 있습니다.");
        }
        if (isLive) {
            throw new ScheduleDomainException("이미 라이브 중인 일정입니다.");
        }
        this.isLive = true;
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public Long getNoticeId() { return noticeId; }
    public String getTitle() { return title; }
    public ArtistScheduleType getType() { return type; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public boolean isLive() { return isLive; }
}