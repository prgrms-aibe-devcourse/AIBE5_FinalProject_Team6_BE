package com.fandrops.community.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 아티스트 프로필 접근 포트 — 구현체: user 모듈 (표지민).
 * ARTIST_PROFILE 테이블 미생성 동안 community-infrastructure에 no-op stub 존재.
 */
public interface ArtistProfilePort {

    boolean exists(Long artistId);

    void incrementFanCount(Long artistId);

    void decrementFanCount(Long artistId);

    /** 입점 승인 시 아티스트 공간을 활성화한다. */
    void activate(Long artistId);

    /** 단건 조회. 아티스트가 없으면 empty. */
    Optional<ArtistSummary> findById(Long artistId);

    /**
     * 다건 일괄 조회. 존재하지 않는 id는 결과 Map에서 누락된다.
     * 빈 컬렉션 입력 시 빈 Map 반환 (DB 호출 없음).
     */
    Map<Long, ArtistSummary> findAllByIds(Collection<Long> artistIds);
}