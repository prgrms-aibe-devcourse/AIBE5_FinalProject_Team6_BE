package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.dto.ParsedClaims;
import com.fandrops.user.application.exception.InvalidTokenException;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderImplTest {

    // "test-secret-key-for-unit-testingonly" Base64 — 36바이트(288bit), HS256 요구치(256bit) 충족
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11bml0LXRlc3Rpbmdvbmx5";

    JwtProviderImpl provider;

    @BeforeEach
    void setUp() {
        provider = new JwtProviderImpl(TEST_SECRET, 1800L, 604800L);
    }

    @Test
    @DisplayName("생성된 액세스 토큰은 올바른 userId·role로 파싱된다")
    void generateAccessToken_parseable_withCorrectClaims() {
        String token = provider.generateAccessToken(42L, UserRole.FAN);

        ParsedClaims claims = provider.parse(token);

        assertEquals(42L, claims.userId());
        assertEquals("FAN", claims.role());
    }

    @Test
    @DisplayName("ADMIN role로 생성된 토큰 파싱 시 role이 ADMIN")
    void generateAccessToken_adminRole_parsedCorrectly() {
        String token = provider.generateAccessToken(99L, UserRole.ADMIN);

        ParsedClaims claims = provider.parse(token);

        assertEquals(99L, claims.userId());
        assertEquals("ADMIN", claims.role());
    }

    @Test
    @DisplayName("리프레시 토큰은 role claim 없어 parse 시 InvalidTokenException")
    void generateRefreshToken_hasNoRoleClaim_parseThrowsInvalidTokenException() {
        String refreshToken = provider.generateRefreshToken(1L);

        assertThrows(InvalidTokenException.class, () -> provider.parse(refreshToken));
    }

    @Test
    @DisplayName("변조된 토큰은 InvalidTokenException")
    void parse_tamperedToken_throwsInvalidTokenException() {
        String token = provider.generateAccessToken(1L, UserRole.FAN);
        String tampered = token + "tampered";

        assertThrows(InvalidTokenException.class, () -> provider.parse(tampered));
    }

    @Test
    @DisplayName("빈 문자열 토큰은 InvalidTokenException")
    void parse_emptyToken_throwsInvalidTokenException() {
        assertThrows(InvalidTokenException.class, () -> provider.parse(""));
    }

    @Test
    @DisplayName("getAccessTokenExpiresIn은 설정된 만료 시간(초)을 반환한다")
    void getAccessTokenExpiresIn_returnsConfiguredValue() {
        assertEquals(1800L, provider.getAccessTokenExpiresIn());
    }
}
