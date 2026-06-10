package com.fandrops.config;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** community-application ↔ user-infrastructure 교차 모듈 DI 연결. */
@Configuration
public class ArtistProfilePortConfig {

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
        };
    }
}
