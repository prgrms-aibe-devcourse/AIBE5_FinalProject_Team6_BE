package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.dto.OAuthUserInfo;
import com.fandrops.user.application.port.OAuthClient;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.infrastructure.config.OAuthProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuthClientImpl implements OAuthClient {

    private final OAuthProperties props;
    private final RestClient restClient;

    public OAuthClientImpl(OAuthProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public OAuthUserInfo getUserInfo(AuthProvider provider, String code) {
        return switch (provider) {
            case GOOGLE -> fetchGoogleUserInfo(code);
            case KAKAO -> fetchKakaoUserInfo(code);
            case LOCAL -> throw new IllegalArgumentException("LOCAL은 OAuth 대상이 아닙니다.");
        };
    }

    private OAuthUserInfo fetchGoogleUserInfo(String code) {
        OAuthProperties.ProviderProperties google = props.google();

        String tokenBody = "code=" + encode(code)
                + "&client_id=" + encode(google.clientId())
                + "&client_secret=" + encode(google.clientSecret())
                + "&redirect_uri=" + encode(google.redirectUri())
                + "&grant_type=authorization_code";

        GoogleTokenResponse tokenResponse = restClient.post()
                .uri(google.tokenUri())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(tokenBody)
                .retrieve()
                .body(GoogleTokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Google 토큰 응답이 비어 있습니다.");
        }

        GoogleUserInfo userInfo = restClient.get()
                .uri(google.userInfoUri())
                .header("Authorization", "Bearer " + tokenResponse.accessToken())
                .retrieve()
                .body(GoogleUserInfo.class);

        if (userInfo == null || userInfo.id() == null) {
            throw new IllegalStateException("Google 사용자 정보 응답이 비어 있습니다.");
        }

        return new OAuthUserInfo(userInfo.id(), userInfo.email(), userInfo.name());
    }

    private OAuthUserInfo fetchKakaoUserInfo(String code) {
        // 카카오 client_id 발급 후 구현 예정
        throw new UnsupportedOperationException("카카오 OAuth 미구현 — client_id 발급 후 별도 PR에서 작업");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record GoogleTokenResponse(@JsonProperty("access_token") String accessToken) {}
    private record GoogleUserInfo(String id, String email, String name) {}
}
