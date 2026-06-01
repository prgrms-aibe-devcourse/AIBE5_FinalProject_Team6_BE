package com.fandrops.community.infrastructure.follow.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowJpaRepository extends JpaRepository<UserFollowJpaEntity, Long> {

    boolean existsByFanIdAndArtistId(Long fanId, Long artistId);
}