package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.PasswordResetTokenStore;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class PasswordResetTokenStoreImpl implements PasswordResetTokenStore {

    @Override
    public String generate(Long fanId) {
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public Optional<Long> findFanIdByToken(String token) {
        return Optional.empty();
    }

    @Override
    public void delete(String token) {}
}
