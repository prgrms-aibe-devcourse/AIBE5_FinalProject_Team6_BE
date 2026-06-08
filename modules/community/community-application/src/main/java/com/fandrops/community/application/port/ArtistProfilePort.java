package com.fandrops.community.application.port;

/**
 * 아티스트 프로필 접근 포트 — 구현체: user 모듈 (표지민).
 * ARTIST_PROFILE 테이블 미생성 동안 community-infrastructure에 no-op stub 존재.
 */
public interface ArtistProfilePort {

    boolean exists(Long artistId);

    void incrementFanCount(Long artistId);

    void decrementFanCount(Long artistId);
}