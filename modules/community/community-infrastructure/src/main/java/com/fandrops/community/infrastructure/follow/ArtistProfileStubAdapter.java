package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.ArtistProfilePort;

/**
 * ARTIST_PROFILE 테이블 미생성 동안 사용하는 no-op 스텁.
 * 빈 등록은 ArtistProfileStubConfig(@Configuration)에서 처리한다.
 * - exists()는 항상 true 반환 (아티스트 존재 가정).
 * - 표지민 실 구현체 등록 시 이 파일과 ArtistProfileStubConfig를 함께 삭제한다.
 *
 * ⚠ 테스트 주의: exists()가 항상 true를 반환하므로 ArtistNotFoundException 경로(404)는
 * 이 스텁으로 검증 불가. 해당 경로를 테스트하려면 @TestConfiguration에서
 * ArtistProfilePort 목(mock)을 직접 빈으로 등록해 exists()=false를 반환하도록 구성할 것.
 */
public class ArtistProfileStubAdapter implements ArtistProfilePort {

    @Override
    public boolean exists(Long artistId) {
        return true;
    }

    @Override
    public void incrementFanCount(Long artistId) {
        // no-op: ARTIST_PROFILE 구현 후 제거
    }

    @Override
    public void decrementFanCount(Long artistId) {
        // no-op: ARTIST_PROFILE 구현 후 제거
    }
}
