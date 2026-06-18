package com.fandrops.user.infrastructure.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenStoreImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    PasswordResetTokenStoreImpl store;

    @BeforeEach
    void setUp() {
        // opsForValue() 는 findFanIdByToken·getAndDelete 에서만 호출 — lenient로 불필요한 stub 검출 방지
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new PasswordResetTokenStoreImpl(redisTemplate, 1800L);
    }

    @Test
    @DisplayName("generate — UUID 형식 토큰 반환, Redis Lua 스크립트 실행")
    void generate_returnsValidUuidToken_andExecutesScript() {
        String token = store.generate(1L);

        assertNotNull(token);
        assertDoesNotThrow(() -> UUID.fromString(token), "토큰이 UUID 형식이어야 한다");
        verify(redisTemplate).execute(any(), any(), any(String.class), any(String.class),
                any(String.class), any(String.class));
    }

    @Test
    @DisplayName("findFanIdByToken — Redis에 값 있으면 Optional<fanId> 반환")
    void findFanIdByToken_exists_returnsOptionalWithFanId() {
        when(valueOps.get("pwd-reset:test-token")).thenReturn("42");

        Optional<Long> result = store.findFanIdByToken("test-token");

        assertTrue(result.isPresent());
        assertEquals(42L, result.get());
    }

    @Test
    @DisplayName("findFanIdByToken — Redis에 값 없으면 Optional.empty()")
    void findFanIdByToken_notFound_returnsEmpty() {
        when(valueOps.get("pwd-reset:missing-token")).thenReturn(null);

        assertTrue(store.findFanIdByToken("missing-token").isEmpty());
    }

    @Test
    @DisplayName("getAndDelete — 토큰 조회 후 토큰·팬 역방향 키 모두 삭제, fanId 반환")
    void getAndDelete_found_deletesBothKeysAndReturnsFanId() {
        when(valueOps.getAndDelete("pwd-reset:del-token")).thenReturn("99");

        Optional<Long> result = store.getAndDelete("del-token");

        assertTrue(result.isPresent());
        assertEquals(99L, result.get());
        verify(redisTemplate).delete("pwd-reset-by-fan:99");
    }

    @Test
    @DisplayName("getAndDelete — 토큰 없으면 Optional.empty(), 역방향 키 삭제 미호출")
    void getAndDelete_notFound_returnsEmpty_doesNotDeleteFanKey() {
        when(valueOps.getAndDelete("pwd-reset:absent-token")).thenReturn(null);

        assertTrue(store.getAndDelete("absent-token").isEmpty());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("delete — 내부적으로 getAndDelete를 호출하여 토큰 제거")
    void delete_callsGetAndDelete() {
        when(valueOps.getAndDelete("pwd-reset:some-token")).thenReturn("7");

        assertDoesNotThrow(() -> store.delete("some-token"));
        verify(valueOps).getAndDelete("pwd-reset:some-token");
    }
}
