package com.fandrops.user.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fandrops.oauth")
public record OAuthProperties(
        ProviderProperties google,
        ProviderProperties kakao
) {
    public record ProviderProperties(
            String clientId,
            String clientSecret,
            String redirectUri,
            String tokenUri,
            String userInfoUri
    ) {}
}
