package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.domain.follow.UserFollow;
import com.fandrops.community.domain.follow.repository.UserFollowRepository;
import com.fandrops.community.infrastructure.follow.jpa.UserFollowJpaEntity;
import com.fandrops.community.infrastructure.follow.jpa.UserFollowJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserFollowRepositoryAdapter implements UserFollowRepository {

    private final UserFollowJpaRepository jpaRepository;

    public UserFollowRepositoryAdapter(UserFollowJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserFollow save(UserFollow follow) {
        UserFollowJpaEntity entity = new UserFollowJpaEntity(
                follow.getId(), follow.getFanId(), follow.getArtistId(), follow.getFollowedAt());
        UserFollowJpaEntity saved = jpaRepository.save(entity);
        return UserFollow.reconstruct(saved.getId(), saved.getFanId(), saved.getArtistId(), saved.getFollowedAt());
    }

    @Override
    public int deleteByFanIdAndArtistId(Long fanId, Long artistId) {
        return jpaRepository.deleteByFanIdAndArtistId(fanId, artistId);
    }
}