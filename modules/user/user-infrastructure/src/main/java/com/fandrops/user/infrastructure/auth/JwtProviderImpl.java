package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.dto.ParsedClaims;
import com.fandrops.user.application.exception.InvalidTokenException;
import com.fandrops.user.application.port.JwtProvider;
import com.fandrops.user.domain.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProviderImpl implements JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpireSeconds;
    private final long refreshTokenExpireSeconds;

    public JwtProviderImpl(
            @Value("${fandrops.jwt.secret}") String secret,
            @Value("${fandrops.jwt.access-token-expire-seconds:1800}") long accessTokenExpireSeconds,
            @Value("${fandrops.jwt.refresh-token-expire-seconds:604800}") long refreshTokenExpireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpireSeconds = accessTokenExpireSeconds;
        this.refreshTokenExpireSeconds = refreshTokenExpireSeconds;
    }

    @Override
    public String generateAccessToken(Long userId, UserRole role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpireSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpireSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    @Override
    public long getAccessTokenExpiresIn() {
        return accessTokenExpireSeconds;
    }

    @Override
    public ParsedClaims parse(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new ParsedClaims(
                    Long.parseLong(claims.getSubject()),
                    claims.get("role", String.class)
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("유효하지 않은 액세스 토큰입니다.", e);
        }
    }
}
