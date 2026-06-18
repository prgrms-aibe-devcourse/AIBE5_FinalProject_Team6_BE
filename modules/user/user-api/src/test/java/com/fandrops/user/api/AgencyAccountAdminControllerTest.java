package com.fandrops.user.api;

import com.fandrops.user.application.exception.InvalidCredentialsException;
import com.fandrops.user.application.service.AgencyAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyAccountAdminControllerTest {

    @Mock AgencyAccountService agencyAccountService;
    @Mock Environment environment;
    @Mock Authentication authentication;
    @Mock HttpServletRequest httpRequest;

    AgencyAccountAdminController controller;

    private static final Long ADMIN_ID = 1L;
    private static final Long AGENCY_ACCOUNT_ID = 100L;

    @BeforeEach
    void setUp() {
        controller = new AgencyAccountAdminController(agencyAccountService, environment);
    }

    @Test
    @DisplayName("관리자 임시 비밀번호 재발급 성공 → 204 No Content, service 올바른 인자로 호출")
    void resetTempPassword_success_returns204AndCallsService() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(ADMIN_ID);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<Void> response = controller.resetTempPassword(
                AGENCY_ACCOUNT_ID, authentication, httpRequest);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(agencyAccountService).resetTempPassword(eq(AGENCY_ACCOUNT_ID), eq(ADMIN_ID),
                eq("127.0.0.1"), anyString());
    }

    @Test
    @DisplayName("인증 정보 없으면 InvalidCredentialsException 발생")
    void resetTempPassword_nullAuthentication_throwsInvalidCredentialsException() {
        assertThrows(InvalidCredentialsException.class,
                () -> controller.resetTempPassword(AGENCY_ACCOUNT_ID, null, httpRequest));
    }
}
