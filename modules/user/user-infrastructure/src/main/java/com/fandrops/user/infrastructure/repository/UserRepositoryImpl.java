package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.UserRepository;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import com.fandrops.user.infrastructure.persistence.FanJpaEntity;
import com.fandrops.user.infrastructure.persistence.FanJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    private final FanJpaRepository fanJpaRepository;

    public UserRepositoryImpl(FanJpaRepository fanJpaRepository) {
        this.fanJpaRepository = fanJpaRepository;
    }

    @Override
    public Fan save(Fan fan) {
        try {
            return fanJpaRepository.save(FanJpaEntity.from(fan)).toDomain();
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("uq_fan_auth_provider_id")) {
                throw new IllegalStateException("이미 등록된 소셜 계정입니다.", e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Fan> findByEmail(String email) {
        return fanJpaRepository.findByEmail(email).map(FanJpaEntity::toDomain);
    }

    @Override
    public Optional<Fan> findById(Long id) {
        return fanJpaRepository.findById(id).map(FanJpaEntity::toDomain);
    }

    @Override
    public Optional<Fan> findByProviderAndProviderId(AuthProvider provider, String providerId) {
        return fanJpaRepository.findByAuthProviderAndProviderId(provider, providerId)
                .map(FanJpaEntity::toDomain);
    }
}
