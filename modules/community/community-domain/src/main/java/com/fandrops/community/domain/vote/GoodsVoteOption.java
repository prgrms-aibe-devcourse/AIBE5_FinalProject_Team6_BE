package com.fandrops.community.domain.vote;

public class GoodsVoteOption {

    private final Long id;
    private final Long voteId;
    private final String label;
    private final String imageUrl;
    private final int voteCount;

    private GoodsVoteOption(Long id, Long voteId, String label, String imageUrl, int voteCount) {
        this.id = id;
        this.voteId = voteId;
        this.label = label;
        this.imageUrl = imageUrl;
        this.voteCount = voteCount;
    }

    public static GoodsVoteOption create(Long voteId, String label, String imageUrl) {
        return new GoodsVoteOption(null, voteId, label, imageUrl, 0);
    }

    public static GoodsVoteOption reconstruct(Long id, Long voteId, String label,
                                               String imageUrl, int voteCount) {
        return new GoodsVoteOption(id, voteId, label, imageUrl, voteCount);
    }

    public Long getId() { return id; }
    public Long getVoteId() { return voteId; }
    public String getLabel() { return label; }
    public String getImageUrl() { return imageUrl; }
    public int getVoteCount() { return voteCount; }
}
