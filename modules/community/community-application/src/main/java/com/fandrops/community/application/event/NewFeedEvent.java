package com.fandrops.community.application.event;

public class NewFeedEvent {

    private final Long feedId;
    private final Long artistId;

    public NewFeedEvent(Long feedId, Long artistId) {
        this.feedId = feedId;
        this.artistId = artistId;
    }

    public Long getFeedId() { return feedId; }
    public Long getArtistId() { return artistId; }
}
