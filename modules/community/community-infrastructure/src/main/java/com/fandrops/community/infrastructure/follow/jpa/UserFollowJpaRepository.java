package com.fandrops.community.infrastructure.follow.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFollowJpaRepository extends JpaRepository<UserFollowJpaEntity, Long> {

    boolean existsByFanIdAndArtistId(Long fanId, Long artistId);

    @Modifying
    @Query("DELETE FROM UserFollowJpaEntity e WHERE e.fanId = :fanId AND e.artistId = :artistId")
    int deleteByFanIdAndArtistId(@Param("fanId") Long fanId, @Param("artistId") Long artistId);
}