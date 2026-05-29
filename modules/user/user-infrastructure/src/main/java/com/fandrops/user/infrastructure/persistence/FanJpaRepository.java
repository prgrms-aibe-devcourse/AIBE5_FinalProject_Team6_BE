package com.fandrops.user.infrastructure.persistence;

import com.fandrops.user.domain.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FanJpaRepository extends JpaRepository<FanJpaEntity, Long> {
    Optional<FanJpaEntity> findByEmail(String email);
    Optional<FanJpaEntity> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
}
