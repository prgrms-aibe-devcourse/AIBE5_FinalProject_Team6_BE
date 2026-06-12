package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.FanMembershipPort;
import com.fandrops.community.infrastructure.follow.jpa.UserFollowJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class FanMembershipAdapter implements FanMembershipPort {

    private final UserFollowJpaRepository jpaRepository;

    public FanMembershipAdapter(UserFollowJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean isFanOf(Long fanId, Long artistId) {
        return jpaRepository.existsByFanIdAndArtistId(fanId, artistId);
    }
}