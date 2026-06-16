package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.*;
import com.fandrops.user.application.exception.*;
import com.fandrops.user.application.port.*;
import com.fandrops.user.domain.AdminAccount;
import com.fandrops.user.domain.AgencyAccount;
import com.fandrops.user.domain.AgencyAccountStatus;
import com.fandrops.user.domain.ArtistMember;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import com.fandrops.user.domain.UserRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock AdminAccountRepository adminAccountRepository;
    @Mock AgencyAccountRepository agencyAccountRepository;
    @Mock ArtistMemberRepository artistMemberRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtProvider jwtProvider;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock PasswordResetTokenStore passwordResetTokenStore;
    @Mock OAuthClient oAuthClient;
    @Mock EmailNotificationPort emailNotificationPort;
    @Mock MeterRegistry meterRegistry;
    @Mock Counter counter;

    AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        when(passwordEncoder.encode("dummy")).thenReturn("$2a$10$mockedDummyHash");
        authService = new AuthService(
                userRepository, adminAccountRepository, agencyAccountRepository,
                artistMemberRepository, passwordEncoder, jwtProvider,
                refreshTokenStore, passwordResetTokenStore, oAuthClient, emailNotificationPort,
                meterRegistry
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
    @DisplayName("소셜 계정 이메일로 로그인 시 동일한 메시지 반환 — Agency/ArtistMember 쿼리 없음 (User Enumeration 방어)")
    void login_socialAccount_throwsSameMessage() {
        LoginCommand command = new LoginCommand("kakao@email.com", "pass");
        Fan socialFan = Fan.builder().email("kakao@email.com").nickname("nick").authProvider(AuthProvider.KAKAO).providerId("12345").build();
        when(userRepository.findByEmail("kakao@email.com")).thenReturn(Optional.of(socialFan));
        when(passwordEncoder.matches("pass", "$2a$10$mockedDummyHash")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        assertEquals("이메일 또는 비밀번호가 일치하지 않습니다.", ex.getMessage());
        // 소셜 팬 발견 시 Agency/ArtistMember DB 조회 없이 early return 검증
        verify(agencyAccountRepository, never()).findByLoginId(anyString());
        verify(artistMemberRepository, never()).findByLoginId(anyString());
    }

    @Test
    @DisplayName("소셜 팬 + Agency 동일 loginId 공존 시 Agency 쿼리 실행 안 됨 — Agency 토큰 발급 방지")
    void login_socialFanAndAgencySameLoginId_agencyNeverQueried() {
        LoginCommand command = new LoginCommand("shared@kakao.com", "agencyPass");
        Fan socialFan = Fan.builder().email("shared@kakao.com").nickname("nick")
                .authProvider(AuthProvider.KAKAO).providerId("kakao-999").build();
        when(userRepository.findByEmail("shared@kakao.com")).thenReturn(Optional.of(socialFan));
        when(passwordEncoder.matches("agencyPass", "$2a$10$mockedDummyHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        // Agency 계정이 존재하더라도 소셜 팬 발견 시점에서 중단 — Agency 토큰 발급 불가
        verify(agencyAccountRepository, never()).findByLoginId(anyString());
        verify(artistMemberRepository, never()).findByLoginId(anyString());
        verify(jwtProvider, never()).generateAccessToken(anyLong(), eq(UserRole.AGENCY));
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

    @Test
    @DisplayName("로컬 Fan + Agency 동일 loginId 공존 시 Fan 우선 — Fan 토큰 반환, Agency 쿼리 없음")
    void login_localFanAndAgencySameLoginId_fanTakesPriority() {
        LoginCommand command = new LoginCommand("shared@test.com", "fanPass");
        Fan fan = Fan.builder().id(1L).email("shared@test.com").nickname("nick")
                .authProvider(AuthProvider.LOCAL).passwordHash("fanHash").build();
        when(userRepository.findByEmail("shared@test.com")).thenReturn(Optional.of(fan));
        when(passwordEncoder.matches("fanPass", "fanHash")).thenReturn(true);
        when(jwtProvider.generateAccessToken(1L, UserRole.FAN)).thenReturn("fan-access");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("fan-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.login(command);

        assertEquals("fan-access", result.accessToken());
        verify(jwtProvider).generateAccessToken(1L, UserRole.FAN);
        verify(jwtProvider, never()).generateAccessToken(anyLong(), eq(UserRole.AGENCY));
        // 순차 조회 — Fan hit 시 Agency/ArtistMember DB 쿼리 미실행 검증
        verify(agencyAccountRepository, never()).findByLoginId(anyString());
        verify(artistMemberRepository, never()).findByLoginId(anyString());
    }

    // ── adminLogin ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Admin 로그인 성공 — role=ADMIN 토큰 발급")
    void adminLogin_success_returnsAdminToken() {
        LoginCommand command = new LoginCommand("admin@fandrops.com", "admin");
        AdminAccount admin = new AdminAccount(100L, "admin@fandrops.com", "adminHash");
        when(adminAccountRepository.findByLoginId("admin@fandrops.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("admin", "adminHash")).thenReturn(true);
        when(jwtProvider.generateAccessToken(100L, UserRole.ADMIN)).thenReturn("admin-access");
        when(jwtProvider.generateRefreshToken(100L)).thenReturn("admin-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.adminLogin(command);

        assertEquals("admin-access", result.accessToken());
        verify(jwtProvider).generateAccessToken(100L, UserRole.ADMIN);
    }

    @Test
    @DisplayName("Admin 비밀번호 불일치 시 InvalidCredentialsException")
    void adminLogin_wrongPassword_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("admin@fandrops.com", "wrong");
        AdminAccount admin = new AdminAccount(100L, "admin@fandrops.com", "adminHash");
        when(adminAccountRepository.findByLoginId("admin@fandrops.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "adminHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.adminLogin(command));
    }

    @Test
    @DisplayName("Admin 미등록 이메일 로그인 시 InvalidCredentialsException (타이밍 공격 방어)")
    void adminLogin_emailNotFound_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("unknown@fandrops.com", "pass");
        when(adminAccountRepository.findByLoginId("unknown@fandrops.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("pass", "$2a$10$mockedDummyHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.adminLogin(command));
        verify(passwordEncoder).matches("pass", "$2a$10$mockedDummyHash");
    }

    // ── login — Agency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Agency 로그인 성공 — role=AGENCY 토큰 발급, ArtistMember 쿼리 없음")
    void login_agency_success_returnsAgencyToken() {
        LoginCommand command = new LoginCommand("agency@fandrops.com", "agencyPass");
        AgencyAccount agency = AgencyAccount.builder()
                .id(10L).loginId("agency@fandrops.com").passwordHash("agencyHash")
                .companyName("FanCorp").contactEmail("agency@fandrops.com")
                .status(AgencyAccountStatus.ACTIVE).build();
        when(userRepository.findByEmail("agency@fandrops.com")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("agency@fandrops.com")).thenReturn(Optional.of(agency));
        when(passwordEncoder.matches("agencyPass", "agencyHash")).thenReturn(true);
        when(jwtProvider.generateAccessToken(10L, UserRole.AGENCY)).thenReturn("agency-access");
        when(jwtProvider.generateRefreshToken(10L)).thenReturn("agency-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.login(command);

        assertEquals("agency-access", result.accessToken());
        verify(jwtProvider).generateAccessToken(10L, UserRole.AGENCY);
        verify(artistMemberRepository, never()).findByLoginId(anyString());
    }

    @Test
    @DisplayName("Agency 비밀번호 불일치 — InvalidCredentialsException")
    void login_agency_wrongPassword_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("agency@fandrops.com", "wrong");
        AgencyAccount agency = AgencyAccount.builder()
                .id(10L).loginId("agency@fandrops.com").passwordHash("agencyHash")
                .companyName("FanCorp").contactEmail("agency@fandrops.com")
                .status(AgencyAccountStatus.ACTIVE).build();
        when(userRepository.findByEmail("agency@fandrops.com")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("agency@fandrops.com")).thenReturn(Optional.of(agency));
        when(passwordEncoder.matches("wrong", "agencyHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
    }

    @Test
    @DisplayName("Agency SUSPENDED 계정 로그인 — 비밀번호 일치해도 InvalidCredentialsException")
    void login_agency_suspended_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("suspended@fandrops.com", "correctPass");
        AgencyAccount suspended = AgencyAccount.builder()
                .id(11L).loginId("suspended@fandrops.com").passwordHash("suspHash")
                .companyName("SuspendedCorp").contactEmail("suspended@fandrops.com")
                .status(AgencyAccountStatus.SUSPENDED).build();
        when(userRepository.findByEmail("suspended@fandrops.com")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("suspended@fandrops.com")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("correctPass", "suspHash")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        verify(jwtProvider, never()).generateAccessToken(anyLong(), any(UserRole.class));
    }

    // ── login — ArtistMember ────────────────────────────────────────────────

    @Test
    @DisplayName("ArtistMember 로그인 성공 — role=ARTIST 토큰 발급")
    void login_artistMember_success_returnsArtistToken() {
        LoginCommand command = new LoginCommand("hani", "memberPass");
        ArtistMember member = ArtistMember.builder()
                .id(20L).artistId(1L).loginId("hani")
                .passwordHash("memberHash").memberName("하니").build();
        when(userRepository.findByEmail("hani")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("hani")).thenReturn(Optional.empty());
        when(artistMemberRepository.findByLoginId("hani")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("memberPass", "memberHash")).thenReturn(true);
        when(jwtProvider.generateAccessToken(20L, UserRole.ARTIST)).thenReturn("artist-access");
        when(jwtProvider.generateRefreshToken(20L)).thenReturn("artist-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.login(command);

        assertEquals("artist-access", result.accessToken());
        verify(jwtProvider).generateAccessToken(20L, UserRole.ARTIST);
    }

    @Test
    @DisplayName("ArtistMember 비밀번호 불일치 — InvalidCredentialsException")
    void login_artistMember_wrongPassword_throwsInvalidCredentials() {
        LoginCommand command = new LoginCommand("hani", "wrong");
        ArtistMember member = ArtistMember.builder()
                .id(20L).artistId(1L).loginId("hani")
                .passwordHash("memberHash").memberName("하니").build();
        when(userRepository.findByEmail("hani")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("hani")).thenReturn(Optional.empty());
        when(artistMemberRepository.findByLoginId("hani")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", "memberHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
    }

    @Test
    @DisplayName("Fan·Agency·ArtistMember 모두 없을 때 — dummy hash로 bcrypt 1회 실행 후 예외")
    void login_noMatchingAccount_runsDummyHashAndThrows() {
        LoginCommand command = new LoginCommand("nobody", "pass");
        when(userRepository.findByEmail("nobody")).thenReturn(Optional.empty());
        when(agencyAccountRepository.findByLoginId("nobody")).thenReturn(Optional.empty());
        when(artistMemberRepository.findByLoginId("nobody")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("pass", "$2a$10$mockedDummyHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(command));
        verify(passwordEncoder).matches("pass", "$2a$10$mockedDummyHash");
    }

    // ── socialLogin ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("소셜 로그인 — 이메일 없는 계정은 로컬 충돌 검사 없이 처리된다")
    void socialLogin_emailNull_skipsConflictCheck() {
        SocialLoginCommand command = new SocialLoginCommand(AuthProvider.KAKAO, "code");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-id", null, "nick");
        when(oAuthClient.getUserInfo(AuthProvider.KAKAO, "code")).thenReturn(userInfo);
        Fan existingFan = Fan.builder().id(5L).nickname("nick").authProvider(AuthProvider.KAKAO).providerId("kakao-id").build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-id")).thenReturn(Optional.of(existingFan));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenReturn("access");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        assertDoesNotThrow(() -> authService.socialLogin(command));
        verify(userRepository, never()).findByEmail(any());
    }

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
    @DisplayName("소셜 로그인 — 동시 요청으로 중복 저장 시 DuplicateSocialAccountException")
    void socialLogin_concurrentDuplicate_throwsDuplicateSocialAccountException() {
        SocialLoginCommand command = new SocialLoginCommand(AuthProvider.KAKAO, "code");
        OAuthUserInfo userInfo = new OAuthUserInfo("kakao-id", "new@email.com", "nick");
        when(oAuthClient.getUserInfo(AuthProvider.KAKAO, "code")).thenReturn(userInfo);
        when(userRepository.findByEmail("new@email.com")).thenReturn(Optional.empty());
        when(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-id")).thenReturn(Optional.empty());
        when(userRepository.save(any(Fan.class))).thenThrow(new DuplicateSocialAccountException("이미 등록된 소셜 계정입니다."));

        assertThrows(DuplicateSocialAccountException.class, () -> authService.socialLogin(command));
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
        when(refreshTokenStore.find("bad-token")).thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class, () -> authService.refreshAccessToken("bad-token"));
    }

    @Test
    @DisplayName("Refresh Token Rotation — 신규 토큰 발급 후 구 토큰 삭제 (발급→삭제 순서 보장)")
    void refreshAccessToken_rotation_issuesNewThenDeletesOld() {
        when(refreshTokenStore.find("old-token")).thenReturn(Optional.of(new RefreshTokenEntry(5L, UserRole.FAN)));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("new-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.refreshAccessToken("old-token");

        InOrder inOrder = inOrder(refreshTokenStore);
        inOrder.verify(refreshTokenStore).find("old-token");
        inOrder.verify(refreshTokenStore).save("new-refresh", 5L, UserRole.FAN);
        inOrder.verify(refreshTokenStore).delete("old-token");
        assertEquals("new-access", result.accessToken());
        assertEquals("new-refresh", result.refreshToken());
    }

    @Test
    @DisplayName("Admin Refresh Token Rotation — role=ADMIN 역할 보존 및 발급→삭제 순서 보장")
    void refreshAccessToken_admin_preservesAdminRole() {
        when(refreshTokenStore.find("admin-old-token")).thenReturn(Optional.of(new RefreshTokenEntry(100L, UserRole.ADMIN)));
        when(jwtProvider.generateAccessToken(100L, UserRole.ADMIN)).thenReturn("new-admin-access");
        when(jwtProvider.generateRefreshToken(100L)).thenReturn("new-admin-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.refreshAccessToken("admin-old-token");

        InOrder inOrder = inOrder(refreshTokenStore);
        inOrder.verify(refreshTokenStore).find("admin-old-token");
        inOrder.verify(refreshTokenStore).save("new-admin-refresh", 100L, UserRole.ADMIN);
        inOrder.verify(refreshTokenStore).delete("admin-old-token");
        verify(jwtProvider).generateAccessToken(100L, UserRole.ADMIN);
        assertEquals("new-admin-access", result.accessToken());
    }

    @Test
    @DisplayName("Refresh Token Rotation — 발급 실패 시 구 토큰 보존 (재시도 가능)")
    void refreshAccessToken_issueTokensFails_oldTokenPreserved() {
        when(refreshTokenStore.find("valid-token")).thenReturn(Optional.of(new RefreshTokenEntry(5L, UserRole.FAN)));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenThrow(new RuntimeException("token generation failure"));

        assertThrows(RuntimeException.class, () -> authService.refreshAccessToken("valid-token"));
        verify(refreshTokenStore).find("valid-token");
        // 발급 실패 시 삭제 미실행 — 구 토큰 보존으로 재시도 가능
        verify(refreshTokenStore, never()).delete(anyString());
        verify(refreshTokenStore, never()).save(anyString(), anyLong(), any(UserRole.class));
    }

    @Test
    @DisplayName("Refresh Token Rotation — delete 실패 시 예외 전파 (새 토큰은 저장됐으나 클라이언트 미수신, 구 토큰 TTL 만료로 자연 소멸)")
    void refreshAccessToken_deleteFails_exceptionPropagates() {
        when(refreshTokenStore.find("old-token")).thenReturn(Optional.of(new RefreshTokenEntry(5L, UserRole.FAN)));
        when(jwtProvider.generateAccessToken(5L, UserRole.FAN)).thenReturn("new-access");
        when(jwtProvider.generateRefreshToken(5L)).thenReturn("new-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);
        doThrow(new RuntimeException("Redis delete fail")).when(refreshTokenStore).delete("old-token");

        assertThrows(RuntimeException.class, () -> authService.refreshAccessToken("old-token"));
        // 새 토큰은 이미 Redis에 저장됨 — orphan으로 TTL(7일) 만료 대기
        verify(refreshTokenStore).save("new-refresh", 5L, UserRole.FAN);
        verify(refreshTokenStore).delete("old-token");
        // delete 실패 시 Prometheus 카운터 증가로 orphan 발생 추적 가능
        verify(meterRegistry).counter("fandrops_token_rotation_delete_errors_total");
        verify(counter).increment();
    }

    @Test
    @DisplayName("Agency SUSPENDED — Refresh Token 갱신 시 InvalidTokenException (보안 결함 #265)")
    void refreshAccessToken_suspendedAgency_throwsInvalidTokenException() {
        AgencyAccount suspended = AgencyAccount.builder()
                .id(11L).loginId("agency@fandrops.com").passwordHash("hash")
                .companyName("Corp").contactEmail("agency@fandrops.com")
                .status(AgencyAccountStatus.SUSPENDED).build();
        when(refreshTokenStore.find("agency-token")).thenReturn(Optional.of(new RefreshTokenEntry(11L, UserRole.AGENCY)));
        when(agencyAccountRepository.findById(11L)).thenReturn(Optional.of(suspended));

        assertThrows(InvalidTokenException.class, () -> authService.refreshAccessToken("agency-token"));
        verify(agencyAccountRepository).findById(11L);
        verify(jwtProvider, never()).generateAccessToken(anyLong(), any(UserRole.class));
    }

    @Test
    @DisplayName("Agency ACTIVE — Refresh Token 갱신 정상 발급 및 DB status 확인")
    void refreshAccessToken_activeAgency_issuesNewToken() {
        AgencyAccount active = AgencyAccount.builder()
                .id(12L).loginId("active@fandrops.com").passwordHash("hash")
                .companyName("Corp").contactEmail("active@fandrops.com")
                .status(AgencyAccountStatus.ACTIVE).build();
        when(refreshTokenStore.find("agency-token")).thenReturn(Optional.of(new RefreshTokenEntry(12L, UserRole.AGENCY)));
        when(agencyAccountRepository.findById(12L)).thenReturn(Optional.of(active));
        when(jwtProvider.generateAccessToken(12L, UserRole.AGENCY)).thenReturn("new-agency-access");
        when(jwtProvider.generateRefreshToken(12L)).thenReturn("new-agency-refresh");
        when(jwtProvider.getAccessTokenExpiresIn()).thenReturn(1800L);

        AuthTokenResult result = authService.refreshAccessToken("agency-token");

        verify(agencyAccountRepository).findById(12L);
        assertEquals("new-agency-access", result.accessToken());
        assertEquals("new-agency-refresh", result.refreshToken());
    }

    // ── confirmPasswordReset ────────────────────────────────────────────────

    @Test
    @DisplayName("만료된 재설정 토큰 시 InvalidTokenException")
    void confirmPasswordReset_invalidToken_throwsInvalidTokenException() {
        when(passwordResetTokenStore.getAndDelete("expired")).thenReturn(Optional.empty());
        assertThrows(InvalidTokenException.class, () -> authService.confirmPasswordReset("expired", "newPass"));
    }

    @Test
    @DisplayName("비밀번호 재설정 — 토큰 유효하나 Fan이 탈퇴된 경우 FanNotFoundException")
    void confirmPasswordReset_validToken_fanDeleted_throwsFanNotFoundException() {
        when(passwordResetTokenStore.getAndDelete("valid-token")).thenReturn(Optional.of(99L));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(FanNotFoundException.class,
                () -> authService.confirmPasswordReset("valid-token", "newPass"));
        verify(userRepository, never()).save(any());
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
    @DisplayName("로컬 계정 비밀번호 재설정 요청 시 토큰 생성 후 이메일 발송")
    void requestPasswordReset_localAccount_generatesTokenAndSendsEmail() {
        Fan localFan = Fan.builder().id(10L).email("local@email.com").nickname("nick")
                .authProvider(AuthProvider.LOCAL).passwordHash("hash").build();
        when(userRepository.findByEmail("local@email.com")).thenReturn(Optional.of(localFan));
        when(passwordResetTokenStore.generate(10L)).thenReturn("reset-token-abc");

        authService.requestPasswordReset("local@email.com");

        verify(passwordResetTokenStore).generate(10L);
        verify(emailNotificationPort).sendPasswordResetEmail("local@email.com", "reset-token-abc");
    }

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
