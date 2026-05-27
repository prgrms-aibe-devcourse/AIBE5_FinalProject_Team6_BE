package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.dto.OAuthUserInfo;
import com.fandrops.user.application.port.OAuthClient;
import com.fandrops.user.domain.AuthProvider;
import org.springframework.stereotype.Component;

@Component
public class OAuthClientImpl implements OAuthClient {

    @Override
    public OAuthUserInfo getUserInfo(AuthProvider provider, String code) {
        throw new UnsupportedOperationException("미구현");
    }
}
