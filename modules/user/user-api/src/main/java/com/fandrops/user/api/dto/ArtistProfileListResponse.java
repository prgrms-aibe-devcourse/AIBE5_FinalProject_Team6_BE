package com.fandrops.user.api.dto;

import com.fandrops.user.application.dto.ArtistProfileListResult;

import java.util.List;

public record ArtistProfileListResponse(
        List<ArtistProfileSummaryResponse> items,
        String nextCursor,
        boolean hasMore
) {
    public static ArtistProfileListResponse from(ArtistProfileListResult result) {
        return new ArtistProfileListResponse(
                result.items().stream().map(ArtistProfileSummaryResponse::from).toList(),
                result.nextCursor(),
                result.hasMore()
        );
    }
}
