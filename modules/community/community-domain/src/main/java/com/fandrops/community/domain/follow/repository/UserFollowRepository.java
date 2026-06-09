package com.fandrops.community.domain.follow.repository;

import com.fandrops.community.domain.follow.UserFollow;

import java.util.List;

public interface UserFollowRepository {

    UserFollow save(UserFollow follow);

    int deleteByFanIdAndArtistId(Long fanId, Long artistId);

    // /fans/me/artists — 팬 가입 아티스트 목록 커서 페이징
    List<UserFollow> findByFanId(Long fanId, Long cursorId, int size);
}
