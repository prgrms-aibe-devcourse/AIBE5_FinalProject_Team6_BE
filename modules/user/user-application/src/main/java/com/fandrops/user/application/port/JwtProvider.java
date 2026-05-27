package com.fandrops.user.application.port;

public interface JwtProvider {
    String generateAccessToken(Long fanId);
    String generateRefreshToken(Long fanId);
    long getAccessTokenExpiresIn();  // 초 단위
}