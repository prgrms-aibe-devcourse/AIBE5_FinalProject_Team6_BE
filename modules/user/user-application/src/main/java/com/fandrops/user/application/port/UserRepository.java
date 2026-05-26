package com.fandrops.user.application.port;

import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;

import java.util.Optional;

public interface
UserRepository {
    Fan save(Fan fan);
    Optional<Fan> findByEmail(String email);
    Optional<Fan> findById(Long id);
    // OAuth 로그인 시 provider + providerId 조합으로 Fan 조회
    Optional<Fan> findByProviderAndProviderId(AuthProvider provider, String providerId);
}