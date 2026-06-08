package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.*;
import com.fandrops.user.application.exception.*;
import com.fandrops.user.application.port.*;
import com.fandrops.user.domain.AdminAccount;
import com.fandrops.user.domain.AuthProvider;
import com.fandrops.user.domain.Fan;
import com.fandrops.user.domain.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordResetTokenStore passwordResetTokenStore;
    private final OAuthClient oAuthClient;
    private final EmailNotificationPort emailNotificationPort;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            AdminAccountRepository adminAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            RefreshTokenStore refreshTokenStore,
            PasswordResetTokenStore passwordResetTokenStore,
            OAuthClient oAuthClient,
            EmailNotificationPort emailNotificationPort) {
        this.userRepository = userRepository;
        this.adminAccountRepository = adminAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.passwordResetTokenStore = passwordResetTokenStore;
        this.oAuthClient = oAuthClient;
        this.emailNotificationPort = emailNotificationPort;
        this.dummyPasswordHash = passwordEncoder.encode("dummy");
    }

    // F01-01: 이메일 회원가입
    @Transactional
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
        return issueTokens(saved.getId(), UserRole.FAN);
    }

    // F01-02: 이메일 로그인 — Fan + Admin 통합 (트랜잭션 불필요, 커넥션 즉시 반납)
    public AuthTokenResult login(LoginCommand command) {
        Fan fan = userRepository.findByEmail(command.email()).orElse(null);
        AdminAccount admin = adminAccountRepository.findByLoginId(command.email()).orElse(null);

        // 타이밍 공격 방어: 후보가 없어도 항상 bcrypt 실행해 응답 시간 평준화
        String hashToCheck;
        if (fan != null && fan.isLocalAccount()) {
            hashToCheck = fan.getPasswordHash();
        } else if (admin != null) {
            hashToCheck = admin.getPasswordHash();
        } else {
            hashToCheck = dummyPasswordHash;
        }
        boolean matches = passwordEncoder.matches(command.password(), hashToCheck);

        if (fan != null && fan.isLocalAccount() && matches) {
            return issueTokens(fan.getId(), UserRole.FAN);
        }
        if (admin != null && matches) {
            return issueTokens(admin.getId(), UserRole.ADMIN);
        }
        throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    // F01-03: 소셜 로그인·가입 (Authorization Code 방식, upsert)
    // 외부 HTTP 호출 포함 — 트랜잭션 없음 (커넥션 풀 고갈 방지)
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

        return issueTokens(fan.getId(), UserRole.FAN);
    }

    // 로그아웃: Redis만 사용 — 트랜잭션 불필요
    public void logout(String refreshToken) {
        refreshTokenStore.delete(refreshToken);
    }

    // Access Token 재발급 (Refresh Token Rotation) — GETDEL로 조회+삭제 원자 처리
    public AuthTokenResult refreshAccessToken(String refreshToken) {
        Long fanId = refreshTokenStore.getAndDelete(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 리프레시 토큰입니다."));
        try {
            return issueTokens(fanId, UserRole.FAN);
        } catch (RuntimeException e) {
            // issueTokens 실패 시 구 토큰 소실 → 재로그인 필요.
            // Redis 장애 확률 < 토큰 재사용 방지를 우선한 의도적 선택.
            throw new InvalidTokenException("토큰 재발급에 실패했습니다. 다시 로그인해 주세요.", e);
        }
    }

    // 비밀번호 재설정 요청 — 이메일 발송 포함, 트랜잭션 없음 (커넥션 풀 고갈 방지)
    // 이메일 존재 여부를 외부에 노출하지 않기 위해 항상 정상 처리
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email)
                .filter(Fan::isLocalAccount)
                .ifPresent(fan -> {
                    String resetToken = passwordResetTokenStore.generate(fan.getId());
                    emailNotificationPort.sendPasswordResetEmail(fan.getEmail(), resetToken);
                });
    }

    // 비밀번호 재설정 확인 — GETDEL로 토큰 조회+삭제 원자 처리 (TOCTOU 방지)
    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        Long fanId = passwordResetTokenStore.getAndDelete(token)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않거나 만료된 재설정 토큰입니다."));

        Fan fan = userRepository.findById(fanId)
                .orElseThrow(() -> new FanNotFoundException("존재하지 않는 팬입니다."));
        fan.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(fan);
    }

    private AuthTokenResult issueTokens(Long userId, UserRole role) {
        String accessToken = jwtProvider.generateAccessToken(userId, role);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenStore.save(refreshToken, userId);
        return new AuthTokenResult(accessToken, refreshToken, jwtProvider.getAccessTokenExpiresIn());
    }

    private String resolveNickname(OAuthUserInfo userInfo) {
        if (userInfo.nickname() != null && !userInfo.nickname().isBlank()) {
            return userInfo.nickname();
        }
        // 이메일 로컬파트 노출 대신 랜덤 닉네임 생성 (개인정보 보호)
        return "fan_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}