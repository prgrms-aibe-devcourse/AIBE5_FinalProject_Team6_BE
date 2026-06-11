package com.fandrops.user.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ArtistProfileJpaRepository extends JpaRepository<ArtistProfileJpaEntity, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE ArtistProfileJpaEntity a SET a.fanCount = a.fanCount + 1 WHERE a.id = :artistId")
    void incrementFanCount(@Param("artistId") Long artistId);

    @Modifying
    @Transactional
    @Query("UPDATE ArtistProfileJpaEntity a SET a.fanCount = a.fanCount - 1 WHERE a.id = :artistId AND a.fanCount > 0")
    void decrementFanCount(@Param("artistId") Long artistId);

    List<ArtistProfileJpaEntity> findAllByOrderByFanCountDescIdAsc(Pageable pageable);

    @Query("SELECT a FROM ArtistProfileJpaEntity a " +
           "WHERE a.fanCount < (SELECT c.fanCount FROM ArtistProfileJpaEntity c WHERE c.id = :cursorId) " +
           "OR (a.fanCount = (SELECT c.fanCount FROM ArtistProfileJpaEntity c WHERE c.id = :cursorId) AND a.id > :cursorId) " +
           "ORDER BY a.fanCount DESC, a.id ASC")
    List<ArtistProfileJpaEntity> findAfterCursor(@Param("cursorId") Long cursorId, Pageable pageable);
}
