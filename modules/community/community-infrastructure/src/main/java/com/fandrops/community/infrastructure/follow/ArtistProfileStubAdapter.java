package com.fandrops.community.infrastructure.follow;

import com.fandrops.community.application.port.ArtistProfilePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * ARTIST_PROFILE 테이블 미생성 동안 사용하는 no-op 스텁.
 * - @ConditionalOnMissingBean: 실 구현체 등록 시 자동으로 이 스텁을 건너뜀 (폴백 역할).
 * - @Profile("!prod"): 프로덕션에서 실 구현체 없이 기동되면 빈 주입 실패로 즉시 감지.
 * - exists()는 로컬/스테이징 편의를 위해 항상 true 반환 (아티스트 존재 가정).
 * - 표지민 실 구현체 등록 시 이 파일을 삭제한다.
 *
 * ⚠ 테스트 주의: exists()가 항상 true를 반환하므로 ArtistNotFoundException 경로(404)는
 * 이 스텁으로 검증 불가. 해당 경로를 테스트하려면 @TestConfiguration에서
 * ArtistProfilePort 목(mock)을 직접 빈으로 등록해 exists()=false를 반환하도록 구성할 것.
 */
@Component
@Profile("!prod")
@ConditionalOnMissingBean(ArtistProfilePort.class)
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
