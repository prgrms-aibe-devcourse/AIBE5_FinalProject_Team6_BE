package com.fandrops.community.application.port;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public interface FeedLikeCachePort {
    Set<Long> getOrLoad(Long fanId, List<Long> feedIds, Supplier<Set<Long>> loader);
    void evictByFanId(Long fanId);
}