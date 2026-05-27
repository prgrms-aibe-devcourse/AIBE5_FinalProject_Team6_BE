package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.JwtProvider;
import com.fandrops.user.domain.UserRole;
import org.springframework.stereotype.Component;

@Component
public class JwtProviderImpl implements JwtProvider {

    @Override
    public String generateAccessToken(Long userId, UserRole role) {
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public String generateRefreshToken(Long userId) {
        throw new UnsupportedOperationException("미구현");
    }

    @Override
    public long getAccessTokenExpiresIn() {
        return 1800L;
    }
}
