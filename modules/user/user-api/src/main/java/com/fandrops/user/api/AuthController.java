package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.*;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.dto.SignUpCommand;
import com.fandrops.user.application.dto.LoginCommand;
import com.fandrops.user.application.dto.SocialLoginCommand;
import com.fandrops.user.application.service.AuthService;
import com.fandrops.user.domain.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends UserControllerSupport {

    private final AuthService authService;

    public AuthController(AuthService authService, Environment environment) {
        super(environment);
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        AuthTokenResult result = authService.signUp(new SignUpCommand(
                request.email(), request.password(), request.nickname(), request.termsAgreed()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResult result = authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/social/{provider}")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        AuthProvider authProvider;
        try {
            authProvider = AuthProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다: " + provider);
        }
        AuthTokenResult result = authService.socialLogin(
                new SocialLoginCommand(authProvider, request.code()));
        return ResponseEntity.ok(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthTokenResult result = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(
                new RefreshResponse(result.accessToken(), result.expiresIn()), traceId()));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthTokenResponse toAuthTokenResponse(AuthTokenResult result) {
        return new AuthTokenResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }
}
