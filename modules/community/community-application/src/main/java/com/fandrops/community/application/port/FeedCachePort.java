package com.fandrops.community.application.port;

import com.fandrops.community.application.feed.FeedListResult;

import java.util.Optional;
import java.util.function.Supplier;

public interface FeedCachePort {
    Optional<FeedListResult> get(Long artistId, Long cursorId, int size);
    void put(Long artistId, Long cursorId, int size, FeedListResult result);
    void evictByArtistId(Long artistId);
    // SingleFlight: 캐시 miss 시 동일 키 진행 중 DB 쿼리가 있으면 대기, 없으면 loader 실행 후 결과 공유
    FeedListResult getOrLoad(Long artistId, Long cursorId, int size, Supplier<FeedListResult> loader);
}