package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.*;
import com.fandrops.user.application.exception.*;
import com.fandrops.user.application.port.*;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import com.fandrops.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock PasswordResetTokenStore passwordResetTokenStore;
    @Mock OAuthClient oAuthClient;
    @Mock EmailNotificationPort emailNotificationPort;

    AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("dummy")).thenReturn("$2a$10$mockedDummyHash");
        authService = new AuthService(
                userRepository, passwordEncoder, jwtProvider,
                refreshTokenStore, passwordResetTokenStore, oAuthClient, emailNotificationPort
        );
    }

    // ── signUp ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("약관 미동의 시 IllegalArgumentException")
    void signUp_termsNotAgreed_throwsIllegalArgumentException() {
        SignUpCommand command = new SignUpCommand("test@email.com", "pass", "nick", false);
        assertThrows(IllegalArgumentException.class, () -> authService.signUp(command));
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("중복 이메일 가입 시 DuplicateEmailException")
    void signUp_duplicateEmail_throwsDuplicateEmailException() {
        SignUpCommand command = new SignUpCommand("dup@email.com", "pass", "nick", true);
        Fan existing = Fan.builder().email("dup@email.com").nickname("old").authProvider(AuthProvider.LOCAL).passwordHash("hash").build();
        when(userRepository.findByEmail("dup@email.com")).thenReturn(Optional.of(existing));

        assertThrows(DuplicateEmailException.class, () -> authService.signUp(command));
    }

    @Test
    @DisplayName("정상 가입 시 토큰 반환")
    void signUp_success_returnsTokens() {
        SignUpCommand command = new SignUpCommand("new@email.com", "pass", "nick", true);
        when(userRepository.findByEmail("new@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        Fan saved = Fan.builder().id(1L).email("new@email.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("hashed").build();
        when(userRepository.save(any(Fan.class))).thenReturn(saved);
        when(jwtProvider.generateAccessToken(1L, UserRole.FAN)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.signUp(command);

        assertEquals("access", result.accessToken());
        assertEquals("refresh", result.refreshToken());
    }

    // ── login ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("미가입 이메일 로그인 시 InvalidCredentialsException (타이밍 공격 방어 — bcrypt 항상 실행)")
    void login_emailNotFound_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("ghost@email.com", "pass");
        when(userRepository.findByEmail("ghost@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("pass", "$2a$10$mockedDummyHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        // dummy hash로 matches()가 반드시 호출됐는지 확인
        verify(passwordEncoder).matches("pass", "$2a$10$mockedDummyHash");
    }

    @Test
    @DisplayName("소셜 계정 이메일로 로그인 시 동일한 메시지 반환 (User Enumeration 방어)")
    void login_socialAccount_throwsSameMessage() {
        LoginCommand command = new LoginCommand("kakao@email.com", "pass");
        Fan socialFan = Fan.builder().email("kakao@email.com").nickname("nick").authProvider(AuthProvider.KAKAO).providerId("12345").build();
        when(userRepository.findByEmail("kakao@email.com")).thenReturn(Optional.of(socialFan));
        when(passwordEncoder.matches("pass", "$2a$10$mockedDummyHash")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        assertEquals("이메일 또는 비밀번호가 일치하지 않습니다.", ex.getMessage());
    }

    @Test
    @DisplayName("비밀번호 불일치 시 InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("local@email.com", "wrong");
        Fan fan = Fan.builder().email("local@email.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("correctHash").build();
        when(userRepository.findByEmail("local@email.com")).thenReturn(Optional.of(fan));
        when(passwordEncoder.matches("wrong", "correctHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
    }

    @Test
    @DisplayName("정상 로그인 시 토큰 반환")
    void login_success_returnsTokens() {
        LoginCommand command = new LoginCommand("local@email.com", "correct");
        Fan fan = Fan.builder().id(2L).email("local@email.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("hash").build();
        when(userRepository.findByEmail("local@email.com")).thenReturn(Optional.of(fan));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(jwtProvider.generateAccessToken(2L, UserRole.FAN)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(2L)).thenReturn("refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.login(command);
        assertEquals("access", result.accessToken());
    }

    // ── socialLogin ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("소셜 로그인 — 같은 이메일로 로컬 계정 존재 시 InvalidCredentialsException")
    void socialLogin_localAccountConflict_throwsInvalidCredentials() {
        SocialLoginCommand command = new SocialLoginCommand(AuthProvider.KAKAO, "code");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-id", "conflict@email.com", "nick");
        when(oAuthClient.getUserInfo(AuthProvider.KAKAO, "code")).thenReturn(userInfo);
        Fan localFan = Fan.builder().email("conflict@email.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("hash").build();
        when(userRepository.findByEmail("conflict@email.com")).thenReturn(Optional.of(localFan));

        assertThrows(InvalidCredentialsException.class, () -> authService.socialLogin(command));
    }

    @Test
    @DisplayName("소셜 로그인 — 기존 팬 존재 시 새로 저장하지 않음")
    void socialLogin_existingFan_returnsTokensWithoutSaving() {
        SocialLoginCommand command = new SocialLoginCommand(AuthProvider.KAKAO, "code");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-id", "existing@email.com", "nick");
        when(oAuthClient.getUserInfo(AuthProvider.KAKAO, "code")).thenReturn(userInfo);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        Fan existingFan = Fan.builder().id(3L).email("existing@email.com").nickname("nick").authProvider(AuthProvider.KAKAO).providerId("kakao-id").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-id")).thenReturn(Optional.of(existingFan));
        when(jwtProvider.generateAccessToken(3L, UserRole.FAN)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(3L)).thenReturn("refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        authService.socialLogin(command);
        verify(userRepository, never()).save(any(Fan.class));
    }

    @Test
    @DisplayName("소셜 로그인 — 신규 팬 저장 후 토큰 반환")
    void socialLogin_newFan_savesAndReturnsTokens() {
        SocialLoginCommand command = new SocialLoginCommand(AuthProvider.GOOGLE, "code");
        OAuthUserInfo userInfo = new OAuthUserInfo("google-id", "new@email.com", null);
        when(oAuthClient.getUserInfo(AuthProvider.GOOGLE, "code")).thenReturn(userInfo);
        when(userRepository.findByEmail("new@email.com")).thenReturn(Optional.empty());
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-id")).thenReturn(Optional.empty());
        Fan saved = Fan.builder().id(4L).email("new@email.com").nickname("fan_abc12345").authProvider(AuthProvider.GOOGLE).providerId("google-id").build();
        when(userRepository.save(any(Fan.class))).thenReturn(saved);
        when(jwtProvider.generateAccessToken(4L, UserRole.FAN)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(4L)).thenReturn("refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.socialLogin(command);
        assertEquals("access", result.accessToken());
        verify(userRepository).save(any(Fan.class));
    }

    // ── refreshAccessToken ──────────────────────────────────────────────────

    @Test
    @DisplayName("유효하지 않은 리프레시 토큰 시 InvalidTokenException")
    void refreshAccessToken_invalidToken_throwsInvalidTokenException() {
        when(refreshTokenStore.getAndDelete("bad-token")).thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class, () -> authService.refreshAccessToken("bad-token"));
    }

    @Test
    @DisplayName("Refresh Token Rotation — GETDEL 원자 처리 후 신규 토큰 발급")
    void refreshAccessToken_rotation_deletesOldAndIssuesNew() {
        when(refreshTokenStore.getAndDelete("old-token")).thenReturn(Optional.of(5L));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("new-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.refreshAccessToken("old-token");

        InOrder inOrder = inOrder(refreshTokenStore);
        inOrder.verify(refreshTokenStore).getAndDelete("old-token");
        inOrder.verify(refreshTokenStore).save("new-refresh", 5L);
        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
    }

    @Test
    @DisplayName("Refresh Token Rotation — 토큰 생성 실패 시 InvalidTokenException (강제 재로그인)")
    void refreshAccessToken_issueTokensFails_throwsInvalidTokenException() {
        when(refreshTokenStore.getAndDelete("valid-token")).thenReturn(Optional.of(5L));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenThrow(new RuntimeException("token generation failure"));

        assertThrows(InvalidTokenException.class, () -> authService.refreshAccessToken("valid-token"));
        verify(refreshTokenStore).getAndDelete("valid-token");
        verify(refreshTokenStore, never()).save(anyString(), anyLong());
    }

    // ── confirmPasswordReset ────────────────────────────────────────────────

    @Test
    @DisplayName("만료된 재설정 토큰 시 InvalidTokenException")
    void confirmPasswordReset_invalidToken_throwsInvalidTokenException() {
        when(passwordResetTokenStore.getAndDelete("expired")).thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class, () -> authService.confirmPasswordReset("expired", "newPass"));
    }

    @Test
    @DisplayName("비밀번호 재설정 — getAndDelete 원자 처리 후 비밀번호 변경")
    void confirmPasswordReset_tokenDeletedBeforePasswordChange() {
        when(passwordResetTokenStore.getAndDelete("valid-token")).thenReturn(Optional.of(6L));
        Fan fan = Fan.builder().id(6L).email("fan@email.com").nickname("nick").authProvider(AuthProvider.LOCAL).passwordHash("oldHash").build();
        when(userRepository.findById(6L)).thenReturn(Optional.of(fan));
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");
        when(userRepository.save(any(Fan.class))).thenReturn(fan);

        authService.confirmPasswordReset("valid-token", "newPass");

        verify(passwordResetTokenStore).getAndDelete("valid-token");
        verify(passwordResetTokenStore, never()).findFanIdByToken(anyString());
        verify(passwordResetTokenStore, never()).delete(anyString());
        verify(userRepository).save(any(Fan.class));
    }

    // ── requestPasswordReset ────────────────────────────────────────────────

    @Test
    @DisplayName("미가입 이메일 재설정 요청 시 예외 없이 정상 처리 (Enumeration 방어)")
    void requestPasswordReset_unregisteredEmail_noException() {
        when(userRepository.findByEmail("nobody@email.com")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> authService.requestPasswordReset("nobody@email.com"));
        verifyNoInteractions(emailNotificationPort);
    }

    @Test
    @DisplayName("소셜 계정 이메일 재설정 요청 시 이메일 발송 안 함")
    void requestPasswordReset_socialAccount_noEmailSent() {
        Fan socialFan = Fan.builder().email("kakao@email.com").nickname("nick").authProvider(AuthProvider.KAKAO).providerId("id").build();
        when(userRepository.findByEmail("kakao@email.com")).thenReturn(Optional.of(socialFan));

        authService.requestPasswordReset("kakao@email.com");
        verifyNoInteractions(emailNotificationPort);
    }

    // ── logout ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 시 refreshToken 삭제")
    void logout_deletesRefreshToken() {
        authService.logout("some-token");
        verify(refreshTokenStore).delete("some-token");
    }

}
