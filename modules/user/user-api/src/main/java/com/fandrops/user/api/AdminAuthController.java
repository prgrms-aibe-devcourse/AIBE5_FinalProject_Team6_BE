package com.fandrops.user.api;

import com.fandrops.common.ApiResponse;
import com.fandrops.user.api.dto.AuthTokenResponse;
import com.fandrops.user.api.dto.LoginRequest;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.dto.LoginCommand;
import com.fandrops.user.application.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController extends UserControllerSupport {

    private final AuthService authService;

    public AdminAuthController(AuthService authService, Environment environment) {
        super(environment);
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> adminLogin(@Valid @RequestBody LoginRequest request) {
        AuthTokenResult result = authService.adminLogin(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(ApiResponse.ok(toAuthTokenResponse(result), traceId()));
    }
}