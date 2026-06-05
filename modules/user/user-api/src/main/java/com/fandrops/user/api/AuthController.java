package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.*;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.dto.SignUpCommand;
import com.fandrops.user.application.dto.LoginCommand;
import com.fandrops.user.application.dto.SocialLoginCommand;
import com.fandrops.user.application.dto.UpdateFanCommand;
import com.fandrops.user.application.service.AuthService;
import com.fandrops.user.domain.AuthProvider;
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
    public ResponseEntity<ApiResponse<AuthTokenResponse>> signUp(@RequestBody SignUpRequest request) {
        AuthTokenResult result = authService.signUp(new SignUpCommand(
                request.email(), request.password(), request.nickname(), request.termsAgreed()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@RequestBody LoginRequest request) {
        AuthTokenResult result = authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/social/{provider}")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> socialLogin(
            @PathVariable String provider,
            @RequestBody SocialLoginRequest request) {
        AuthTokenResult result = authService.socialLogin(
                new SocialLoginCommand(AuthProvider.valueOf(provider), request.code()));
        return ResponseEntity.ok(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@RequestBody RefreshRequest request) {
        AuthTokenResult result = authService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(
                new RefreshResponse(result.accessToken(), result.expiresIn()), traceId()));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody PasswordResetRequestDto request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private AuthTokenResponse toAuthTokenResponse(AuthTokenResult result) {
        return new AuthTokenResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }
}
