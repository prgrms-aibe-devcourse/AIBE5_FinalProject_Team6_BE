package com.fandrops.community.domain.vote.repository;

import com.fandrops.community.domain.vote.GoodsVote;

import java.util.List;
import java.util.Optional;

public interface GoodsVoteRepository {

    GoodsVote save(GoodsVote vote);

    Optional<GoodsVote> findById(Long id);

    // 커서 페이징: cursorId 미만, artist_id 필터, id DESC
    List<GoodsVote> findByArtistId(Long artistId, Long cursorId, int size);
}
