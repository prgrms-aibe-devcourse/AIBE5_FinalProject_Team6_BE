package com.fandrops.community.application.event;

public class NewCommentEvent {

    private final Long commentId;
    private final Long feedId;
    private final Long parentId;
    private final Long artistId;

    public NewCommentEvent(Long commentId, Long feedId, Long parentId, Long artistId) {
        this.commentId = commentId;
        this.feedId = feedId;
        this.parentId = parentId;
        this.artistId = artistId;
    }

    public Long getCommentId() { return commentId; }
    public Long getFeedId() { return feedId; }
    public Long getParentId() { return parentId; }
    public Long getArtistId() { return artistId; }
}
