package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.port.RefreshTokenEntry;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    RefreshTokenStoreImpl store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RefreshTokenStoreImpl(redisTemplate, 604800L);
    }

    // ── find ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("find — 유효한 토큰 조회 시 userId·role 정상 파싱")
    void find_validToken_returnsEntry() {
        when(valueOps.get("refresh:test-token")).thenReturn("42:ADMIN");

        Optional<RefreshTokenEntry> result = store.find("test-token");

        assertTrue(result.isPresent());
        assertEquals(42L, result.get().userId());
        assertEquals(UserRole.ADMIN, result.get().role());
    }

    @Test
    @DisplayName("find — 존재하지 않는 토큰 조회 시 Optional.empty()")
    void find_missingToken_returnsEmpty() {
        when(valueOps.get("refresh:missing-token")).thenReturn(null);

        assertTrue(store.find("missing-token").isEmpty());
    }

    @Test
    @DisplayName("find — 구 포맷(userId만 저장) 토큰은 FAN 역할로 하위호환 파싱")
    void find_legacyFormat_returnsFanRole() {
        when(valueOps.get("refresh:legacy-token")).thenReturn("99");

        Optional<RefreshTokenEntry> result = store.find("legacy-token");

        assertTrue(result.isPresent());
        assertEquals(99L, result.get().userId());
        assertEquals(UserRole.FAN, result.get().role());
    }

    @Test
    @DisplayName("find — 오염된 Redis 값은 Optional.empty() 반환 (예외 미전파)")
    void find_corruptedValue_returnsEmpty() {
        when(valueOps.get("refresh:bad-token")).thenReturn("not-valid-data!!!@#");

        assertTrue(store.find("bad-token").isEmpty());
    }

    @Test
    @DisplayName("find — 알 수 없는 role 값은 Optional.empty() 반환")
    void find_unknownRole_returnsEmpty() {
        when(valueOps.get("refresh:unknown-role-token")).thenReturn("42:SUPERUSER");

        assertTrue(store.find("unknown-role-token").isEmpty());
    }
}
