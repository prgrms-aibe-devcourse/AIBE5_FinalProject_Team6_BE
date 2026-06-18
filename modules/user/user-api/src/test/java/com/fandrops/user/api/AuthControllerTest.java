package com.fandrops.user.api;

import com.fandrops.user.api.dto.*;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock Environment environment;

    AuthController controller;

    private static final AuthTokenResult TOKEN_RESULT =
            new AuthTokenResult("access-token", "refresh-token", 1800L);

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, environment);
    }

    @Test
    @DisplayName("회원가입 성공 → 201 Created + 토큰 반환")
    void signUp_success_returns201WithTokens() {
        when(authService.signUp(any())).thenReturn(TOKEN_RESULT);

        ResponseEntity<?> response = controller.signUp(
                new SignUpRequest("user@test.com", "Password123!", "nickname", true));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(authService).signUp(any());
    }

    @Test
    @DisplayName("로그인 성공 → 200 OK + 토큰 반환")
    void login_success_returns200WithTokens() {
        when(authService.login(any())).thenReturn(TOKEN_RESULT);

        ResponseEntity<?> response = controller.login(
                new LoginRequest("user@test.com", "Password123!"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).login(any());
    }

    @Test
    @DisplayName("소셜 로그인 GOOGLE → 200 OK + 토큰 반환")
    void socialLogin_googleProvider_returns200WithTokens() {
        when(authService.socialLogin(any())).thenReturn(TOKEN_RESULT);

        ResponseEntity<?> response = controller.socialLogin("GOOGLE",
                new SocialLoginRequest("auth-code-123"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).socialLogin(any());
    }

    @Test
    @DisplayName("소셜 로그인 미지원 provider → IllegalArgumentException, service 미호출")
    void socialLogin_unsupportedProvider_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.socialLogin("INVALID_PROVIDER_XYZ",
                        new SocialLoginRequest("auth-code-123")));
        verify(authService, never()).socialLogin(any());
    }

    @Test
    @DisplayName("로그아웃 성공 → 204 No Content, refreshToken 전달")
    void logout_success_returns204() {
        ResponseEntity<Void> response = controller.logout(
                new LogoutRequest("refresh-token-abc"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).logout("refresh-token-abc");
    }

    @Test
    @DisplayName("토큰 갱신 성공 → 200 OK + 새 토큰 반환")
    void refresh_success_returns200WithNewTokens() {
        when(authService.refreshAccessToken(any())).thenReturn(TOKEN_RESULT);

        ResponseEntity<?> response = controller.refresh(
                new RefreshRequest("old-refresh-token"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).refreshAccessToken("old-refresh-token");
    }

    @Test
    @DisplayName("비밀번호 재설정 요청 → 204 No Content, email 전달")
    void requestPasswordReset_success_returns204() {
        ResponseEntity<Void> response = controller.requestPasswordReset(
                new PasswordResetRequest("user@test.com"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).requestPasswordReset("user@test.com");
    }

    @Test
    @DisplayName("비밀번호 재설정 확인 → 204 No Content, token·newPassword 전달")
    void confirmPasswordReset_success_returns204() {
        ResponseEntity<Void> response = controller.confirmPasswordReset(
                new PasswordResetConfirmRequest("reset-token", "NewPassword123!"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(authService).confirmPasswordReset("reset-token", "NewPassword123!");
    }
}
