package com.fandrops.config;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** community-application ↔ user-infrastructure 교차 모듈 DI 연결. */
@Configuration
public class ArtistProfilePortConfig {

    @Primary
    @Bean
    public ArtistProfilePort artistProfilePort(ArtistProfileJpaRepository repository) {
        return new ArtistProfilePort() {
            @Override
            public boolean exists(Long artistId) {
                return repository.existsById(artistId);
            }

            @Override
            public void incrementFanCount(Long artistId) {
                repository.incrementFanCount(artistId);
            }

            @Override
            public void decrementFanCount(Long artistId) {
                repository.decrementFanCount(artistId);
            }

            @Override
            public void activate(Long artistId) {
                // TODO: artist_profile에 active 컬럼 추가 후 실 구현
            }
        };
    }
}
