package com.fandrops.notification.application.port;

import java.util.List;
import java.util.Optional;

public interface FanIdResolverPort {
    Optional<Long> findFanIdByOrderId(Long orderId);
    List<Long> findFollowerFanIdsByArtistId(Long artistId);
    Optional<Long> findFanIdByCommentId(Long commentId);
}