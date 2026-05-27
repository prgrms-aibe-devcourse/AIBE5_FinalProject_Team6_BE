package com.fandrops.user.infrastructure.repository;

import com.fandrops.user.application.port.UserRepository;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryImpl implements UserRepository {

    @Override
    public Fan save(Fan fan) {
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public Optional<Fan> findByEmail(String email) {
        return Optional.empty();
    }

    @Override
    public Optional<Fan> findById(Long id) {
        return Optional.empty();
    }

    @Override
    public Optional<Fan> findByProviderAndProviderId(AuthProvider provider, String providerId) {
        return Optional.empty();
    }
}
