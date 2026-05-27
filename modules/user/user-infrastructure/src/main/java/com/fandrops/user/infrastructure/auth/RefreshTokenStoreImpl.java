package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.RefreshTokenStore;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RefreshTokenStoreImpl implements RefreshTokenStore {

    @Override
    public void save(String refreshToken, Long fanId) {}

    @Override
    public Optional<Long> findFanIdByToken(String refreshToken) {
        return Optional.empty();
    }

    @Override
    public void delete(String refreshToken) {}
}
