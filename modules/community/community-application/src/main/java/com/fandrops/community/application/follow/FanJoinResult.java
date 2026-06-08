package com.fandrops.community.application.follow;

import java.time.OffsetDateTime;

public record FanJoinResult(Long artistId, Long fanId, OffsetDateTime followedAt) {
}