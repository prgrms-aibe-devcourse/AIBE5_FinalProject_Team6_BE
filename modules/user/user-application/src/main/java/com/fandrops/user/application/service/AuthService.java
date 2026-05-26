package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.*;
import com.fandrops.user.application.exception.*;
import com.fandrops.user.application.port.*;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final OAuthClient oAuthClient;
    private final EmailNotificationPort emailNotificationPort;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            PasswordResetTokenStore passwordResetTokenStore,
            OAuthClient oAuthClient,
            EmailNotificationPort emailNotificationPort) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordResetTokenStore = passwordResetTokenStore;
        this.oAuthClient = oAuthClient;
        this.emailNotificationPort = emailNotificationPort;
    }

    // F01-01: 이메일 회원가입
    public AuthTokenResult signUp(SignUpCommand command) {
        if (!command.termsAgreed()) {
            throw new IllegalArgumentException("이용약관에 동의해야 합니다.");
        }

        if (userRepository.findByEmail(command.email()).isPresent()) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }

        Fan fan = Fan.builder()
                .email(command.email())
                .nickname(command.nickname())
                .authProvider(AuthProvider.LOCAL)
                .passwordHash(passwordEncoder.encode(command.password()))
                .build();

        Fan saved = userRepository.save(fan);
        return issueTokens(saved.getId());
    }

    // F01-02: 이메일 로그인
    public AuthTokenResult login(LoginCommand command) {
        Fan fan = userRepository.findByEmail(command.email())
                .orElseThrow(() -> new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다."));

        if (!fan.isLocalAccount()) {
            throw new InvalidCredentialsException("소셜 로그인 계정입니다. 카카오 또는 구글로 로그인해 주세요.");
        }

        if (!passwordEncoder.matches(command.password(), fan.getPasswordHash())) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        return issueTokens(fan.getId());
    }

    // F01-03: 소셜 로그인·가입 (Authorization Code 방식, upsert)
    public AuthTokenResult socialLogin(SocialLoginCommand command) {
        OAuthUserInfo userInfo = oAuthClient.getUserInfo(command.provider(), command.code());

        if (userInfo.email() != null) {
            userRepository.findByEmail(userInfo.email())
                    .filter(Fan::isLocalAccount)
                    .ifPresent(existing -> {
                        throw new InvalidCredentialsException("이미 이메일/비밀번호로 가입된 계정입니다. 일반 로그인을 이용해 주세요.");
                    });
        }

        Fan fan = userRepository.findByProviderAndProviderId(command.provider(), userInfo.providerId())
                .orElseGet(() -> {
                    Fan newFan = Fan.builder()
                            .email(userInfo.email())
                            .nickname(resolveNickname(userInfo))
                            .authProvider(command.provider())
                            .providerId(userInfo.providerId())
                            .build();
                    return userRepository.save(newFan);
                });

        return issueTokens(fan.getId());
    }

    // 로그아웃: Redis에서 Refresh 토큰 삭제
    public void logout(String refreshToken) {
        refreshTokenStore.delete(refreshToken);
    }

    // Access Token 재발급 (Refresh Token Rotation)
    public AuthTokenResult refreshAccessToken(String refreshToken) {
        Long fanId = refreshTokenStore.findFanIdByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));

        refreshTokenStore.delete(refreshToken);
        return issueTokens(fanId);
    }

    // 비밀번호 재설정 요청 — 이메일 존재 여부를 외부에 노출하지 않기 위해 항상 정상 처리
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email)
                .filter(Fan::isLocalAccount)
                .ifPresent(fan -> {
                    String resetToken = passwordResetTokenStore.generate(fan.getId());
                    emailNotificationPort.sendPasswordResetEmail(fan.getEmail(), resetToken);
                });
    }

    // 비밀번호 재설정 확인
    public void confirmPasswordReset(String token, String newPassword) {
        Long fanId = passwordResetTokenStore.findFanIdByToken(token)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않거나 만료된 재설정 토큰입니다."));

        Fan fan = userRepository.findById(fanId)
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));

        fan.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(fan);
        passwordResetTokenStore.delete(token);
    }

    // 내 정보 조회
    @Transactional(readOnly = true)
    public FanResult getMyInfo(Long fanId) {
        Fan fan = userRepository.findById(fanId)
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));
        return FanResult.from(fan);
    }

    // 내 정보 수정
    public FanResult updateMyInfo(UpdateFanCommand command) {
        Fan fan = userRepository.findById(command.fanId())
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));

        if (command.nickname() != null) {
            fan.updateNickname(command.nickname());
        }
        if (command.allowNotification() != null) {
            fan.updateNotificationConsent(command.allowNotification());
        }

        return FanResult.from(userRepository.save(fan));
    }

    private AuthTokenResult issueTokens(Long fanId) {
        String accessToken = jwtProvider.generateAccessToken(fanId);
        String refreshToken = jwtProvider.generateRefreshToken(fanId);
        refreshTokenStore.save(refreshToken, fanId);
        return new AuthTokenResult(accessToken, refreshToken, jwtProvider.getAccessTokenExpiresIn());
    }

    private String resolveNickname(OAuthUserInfo userInfo) {
        if (userInfo.nickname() != null && !userInfo.nickname().isBlank()) {
            return userInfo.nickname();
        }
        return userInfo.email().split("@")[0];
    }
}
