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
    private final String liveUrl;
    private final String content;

    private ArtistSchedule(Long id, Long artistId, Long noticeId, String title,
                           ArtistScheduleType type, LocalDateTime scheduledAt,
                           String liveUrl, String content) {
        this.id = id;
        this.artistId = artistId;
        this.noticeId = noticeId;
        this.title = title;
        this.type = type;
        this.scheduledAt = scheduledAt;
        this.liveUrl = liveUrl;
        this.content = content;
    }

    public static ArtistSchedule create(Long artistId, String title,
                                        ArtistScheduleType type, LocalDateTime scheduledAt) {
        if (artistId == null) {
            throw new ScheduleDomainException("artistId는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new ScheduleDomainException("title은 1~255자여야 합니다.");
        }
        if (scheduledAt == null) {
            throw new ScheduleDomainException("scheduledAt은 필수입니다.");
        }
        return new ArtistSchedule(null, artistId, null, title, type, scheduledAt, null, null);
    }

    public static ArtistSchedule createNotice(Long artistId, String title,
                                               String content, LocalDateTime scheduledAt) {
        if (artistId == null) {
            throw new ScheduleDomainException("artistId는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new ScheduleDomainException("title은 1~255자여야 합니다.");
        }
        if (scheduledAt == null) {
            throw new ScheduleDomainException("scheduledAt은 필수입니다.");
        }
        return new ArtistSchedule(null, artistId, null, title, ArtistScheduleType.NOTICE,
                scheduledAt, null, content);
    }

    public static ArtistSchedule createLive(Long artistId, String title,
                                             LocalDateTime scheduledAt, String liveUrl) {
        if (artistId == null) {
            throw new ScheduleDomainException("artistId는 필수입니다.");
        }
        if (title == null || title.isBlank() || title.length() > 255) {
            throw new ScheduleDomainException("title은 1~255자여야 합니다.");
        }
        if (scheduledAt == null) {
            throw new ScheduleDomainException("scheduledAt은 필수입니다.");
        }
        return new ArtistSchedule(null, artistId, null, title, ArtistScheduleType.LIVE,
                scheduledAt, liveUrl, null);
    }

    public static ArtistSchedule reconstruct(Long id, Long artistId, Long noticeId, String title,
                                              ArtistScheduleType type, LocalDateTime scheduledAt,
                                              String liveUrl) {
        return new ArtistSchedule(id, artistId, noticeId, title, type, scheduledAt, liveUrl, null);
    }

    public static ArtistSchedule reconstruct(Long id, Long artistId, Long noticeId, String title,
                                              ArtistScheduleType type, LocalDateTime scheduledAt,
                                              String liveUrl, String content) {
        return new ArtistSchedule(id, artistId, noticeId, title, type, scheduledAt, liveUrl, content);
    }

    public Long getId() { return id; }
    public Long getArtistId() { return artistId; }
    public Long getNoticeId() { return noticeId; }
    public String getTitle() { return title; }
    public ArtistScheduleType getType() { return type; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getLiveUrl() { return liveUrl; }
    public String getContent() { return content; }
}
