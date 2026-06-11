package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.ArtistProfilePort;
import org.springframework.stereotype.Component;

// TODO: artist_profile.active 컬럼 추가 완료 시
// 이 파일과 ArtistProfileStubConfig.java 함께 삭제
// ArtistProfilePortConfig의 activate() 실 구현 후 제거

/**
 * ARTIST_PROFILE 테이블 미생성 / user 모듈 구현체 미연결 동안 사용하는 no-op stub.
 * user 모듈의 실제 구현체로 교체되면 이 클래스를 삭제한다.
 */
@Component
public class ArtistProfileStubAdapter implements ArtistProfilePort {

    @Override
    public boolean exists(Long artistId) {
        return true;
    }

    @Override
    public void incrementFanCount(Long artistId) {
    }

    @Override
    public void decrementFanCount(Long artistId) {
    }

    @Override
    public void activate(Long artistId) {
    }
}