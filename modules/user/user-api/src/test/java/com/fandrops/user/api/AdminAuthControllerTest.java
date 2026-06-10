package com.fandrops.user.api;

import com.fandrops.user.api.dto.LoginRequest;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.dto.LoginCommand;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerTest {

    @Mock AuthService authService;
    @Mock Environment environment;

    AdminAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuthController(authService, environment);
    }

    @Test
    @DisplayName("관리자 로그인 성공 → 200 OK, 토큰 반환")
    void adminLogin_success_returns200WithToken() {
        LoginRequest request = new LoginRequest("admin@test.com", "pass123");
        AuthTokenResult stub = new AuthTokenResult("access-token", "refresh-token", 3600L);
        when(authService.adminLogin(any(LoginCommand.class))).thenReturn(stub);

        ResponseEntity<?> response = controller.adminLogin(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).adminLogin(any(LoginCommand.class));
    }
}
