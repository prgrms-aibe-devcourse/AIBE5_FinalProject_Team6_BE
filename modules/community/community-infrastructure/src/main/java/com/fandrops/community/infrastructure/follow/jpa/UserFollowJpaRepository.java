package com.fandrops.community.infrastructure.follow.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFollowJpaRepository extends JpaRepository<UserFollowJpaEntity, Long> {

    boolean existsByFanIdAndArtistId(Long fanId, Long artistId);

    @Modifying
    @Query("DELETE FROM UserFollowJpaEntity e WHERE e.fanId = :fanId AND e.artistId = :artistId")
    int deleteByFanIdAndArtistId(@Param("fanId") Long fanId, @Param("artistId") Long artistId);

    // /fans/me/artists 커서 페이징 (idx_user_follow_fan 사용)
    List<UserFollowJpaEntity> findByFanIdOrderByIdDesc(Long fanId, Pageable pageable);

    List<UserFollowJpaEntity> findByFanIdAndIdLessThanOrderByIdDesc(Long fanId, Long id, Pageable pageable);
}