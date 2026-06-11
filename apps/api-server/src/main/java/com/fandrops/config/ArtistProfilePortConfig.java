package com.fandrops.config;

import com.fandrops.community.application.port.ArtistProfilePort;
import com.fandrops.community.application.port.ArtistSummary;
import com.fandrops.user.domain.ArtistProfile;
import com.fandrops.user.infrastructure.persistence.ArtistProfileJpaRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

            @Override
            public Optional<ArtistSummary> findById(Long artistId) {
                return repository.findById(artistId)
                        .map(e -> toSummary(e.toDomain()));
            }

            @Override
            public Map<Long, ArtistSummary> findAllByIds(Collection<Long> artistIds) {
                if (artistIds.isEmpty()) return Map.of();
                return repository.findAllById(artistIds).stream()
                        .map(e -> e.toDomain())
                        .collect(Collectors.toMap(
                                ArtistProfile::getId,
                                p -> toSummary(p)));
            }

            private ArtistSummary toSummary(ArtistProfile p) {
                return new ArtistSummary(p.getId(), p.getName(), p.getProfileImageUrl());
            }
        };
    }
}