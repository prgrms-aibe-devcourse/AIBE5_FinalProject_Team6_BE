package com.fandrops.user.application.port;

import com.fandrops.user.application.dto.OAuthUserInfo;
import com.fandrops.user.domain.AuthProvider;

// Authorization Code 방식: provider 서버에서 유저 정보 조회
public interface OAuthClient {
    OAuthUserInfo getUserInfo(AuthProvider provider, String code);
}