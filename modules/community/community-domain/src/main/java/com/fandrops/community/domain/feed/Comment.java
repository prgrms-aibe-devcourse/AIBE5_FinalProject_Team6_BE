package com.fandrops.community.domain.feed;

import com.fandrops.community.domain.feed.exception.FeedDomainException;

import java.time.Clock;
import java.time.LocalDateTime;

public class Comment {

    private Long id;
    private final Long feedId;
    // 마이페이지 히스토리 조회 시 JOIN 없이 바로 참조하기 위해 비정규화 보관 (ERD §5.3)
    private final Long artistId;
    private final Long fanId;
    private final Long artistMemberId;
    private final Long parentId;
    private String content;
    private final LocalDateTime createdAt;

    private Comment(Long feedId, Long artistId, Long fanId, Long artistMemberId,
                    Long parentId, String content, Clock clock) {
        validate(fanId, artistMemberId);
        this.feedId = feedId;
        this.artistId = artistId;
        this.fanId = fanId;
        this.artistMemberId = artistMemberId;
        this.parentId = parentId;
        this.content = content;
        this.createdAt = LocalDateTime.now(clock);
    }

    private Comment(Long id, Long feedId, Long artistId, Long fanId, Long artistMemberId,
                    Long parentId, String content, LocalDateTime createdAt) {
        this.id = id;
        this.feedId = feedId;
        this.artistId = artistId;
        this.fanId = fanId;
        this.artistMemberId = artistMemberId;
        this.parentId = parentId;
        this.content = content;
        this.createdAt = createdAt;
    }

    public static Comment createByFan(Long feedId, Long artistId, Long fanId,
                                      Long parentId, String content, Clock clock) {
        return new Comment(feedId, artistId, fanId, null, parentId, content, clock);
    }

    public static Comment createByArtistMember(Long feedId, Long artistId,
                                                Long artistMemberId, Long parentId, String content, Clock clock) {
        if (parentId == null) {
            throw new FeedDomainException("아티스트 멤버는 팬 댓글에 대한 답글만 작성할 수 있습니다.");
        }
        return new Comment(feedId, artistId, null, artistMemberId, parentId, content, clock);
    }

    public static Comment reconstruct(Long id, Long feedId, Long artistId, Long fanId,
                                       Long artistMemberId, Long parentId,
                                       String content, LocalDateTime createdAt) {
        validate(fanId, artistMemberId);
        return new Comment(id, feedId, artistId, fanId, artistMemberId, parentId, content, createdAt);
    }

    // ERD §5.3: fanId XOR artistMemberId
    private static void validate(Long fanId, Long artistMemberId) {
        boolean hasFan = fanId != null;
        boolean hasArtist = artistMemberId != null;
        if (hasFan == hasArtist) {
            throw new FeedDomainException("댓글 작성자는 fanId 또는 artistMemberId 중 하나만 지정해야 합니다.");
        }
    }

    public boolean isTopLevel() {
        return parentId == null;
    }

    public boolean isWrittenByArtistMember() {
        return artistMemberId != null;
    }

    public Long getId() { return id; }
    public Long getFeedId() { return feedId; }
    public Long getArtistId() { return artistId; }
    public Long getFanId() { return fanId; }
    public Long getArtistMemberId() { return artistMemberId; }
    public Long getParentId() { return parentId; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}