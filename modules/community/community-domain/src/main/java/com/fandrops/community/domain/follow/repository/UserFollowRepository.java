package com.fandrops.community.domain.follow.repository;

import com.fandrops.community.domain.follow.UserFollow;

public interface UserFollowRepository {

    UserFollow save(UserFollow follow);

    int deleteByFanIdAndArtistId(Long fanId, Long artistId);
}
