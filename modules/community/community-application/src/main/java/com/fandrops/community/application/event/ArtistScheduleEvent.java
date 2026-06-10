package com.fandrops.community.application.event;

public class ArtistScheduleEvent {

    private final Long scheduleId;
    private final Long artistId;

    public ArtistScheduleEvent(Long scheduleId, Long artistId) {
        this.scheduleId = scheduleId;
        this.artistId = artistId;
    }

    public Long getScheduleId() { return scheduleId; }
    public Long getArtistId() { return artistId; }
}
