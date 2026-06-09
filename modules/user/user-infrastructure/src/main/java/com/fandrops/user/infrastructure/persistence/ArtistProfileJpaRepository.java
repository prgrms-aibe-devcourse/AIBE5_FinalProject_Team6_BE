package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ArtistProfileJpaRepository extends JpaRepository<ArtistProfileJpaEntity, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE ArtistProfileJpaEntity a SET a.fanCount = a.fanCount + 1 WHERE a.id = :artistId")
    void incrementFanCount(@Param("artistId") Long artistId);

    @Modifying
    @Transactional
    @Query("UPDATE ArtistProfileJpaEntity a SET a.fanCount = a.fanCount - 1 WHERE a.id = :artistId AND a.fanCount > 0")
    void decrementFanCount(@Param("artistId") Long artistId);
}
