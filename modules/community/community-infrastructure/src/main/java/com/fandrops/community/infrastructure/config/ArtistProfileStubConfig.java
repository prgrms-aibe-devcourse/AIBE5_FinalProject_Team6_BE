package com.fandrops.community.infrastructure.config;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.community.infrastructure.follow.ArtistProfileStubAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * ArtistProfilePort 실 구현체가 없는 환경(로컬·스테이징)에서 스텁을 폴백으로 등록한다.
 * @Bean + @ConditionalOnMissingBean 조합은 컴포넌트 스캔 완료 후 평가되므로
 * @Component + @ConditionalOnMissingBean보다 빈 등록 순서에 안전하다.
 * 표지민 실 구현체 등록 시 이 파일과 ArtistProfileStubAdapter를 함께 삭제한다.
 */
@Configuration
@Profile("!prod")
public class ArtistProfileStubConfig {

    @Bean
    @ConditionalOnMissingBean(ArtistProfilePort.class)
    public ArtistProfilePort artistProfilePort() {
        return new ArtistProfileStubAdapter();
    }
}