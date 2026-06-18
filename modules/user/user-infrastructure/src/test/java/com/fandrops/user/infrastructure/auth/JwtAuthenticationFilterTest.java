package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.dto.ParsedClaims;
import com.fandrops.user.application.exception.InvalidTokenException;
import com.fandrops.user.application.port.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock JwtProvider jwtProvider;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;

    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtProvider);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 Bearer 토큰 → SecurityContext에 인증 정보 설정, 체인 계속")
    void doFilterInternal_validBearerToken_setsSecurityContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        when(jwtProvider.parse("valid.jwt.token")).thenReturn(new ParsedClaims(1L, "FAN"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(1L, auth.getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("유효하지 않은 토큰 → SecurityContext 미설정, 체인 계속 (401은 Security가 처리)")
    void doFilterInternal_invalidToken_doesNotSetContext_chainContinues() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token");
        when(jwtProvider.parse("invalid.token")).thenThrow(new InvalidTokenException("invalid"));

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → parse 미호출, 체인 계속")
    void doFilterInternal_noAuthorizationHeader_doesNotCallParse() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtProvider, never()).parse(any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 아닌 Authorization 헤더 → parse 미호출")
    void doFilterInternal_nonBearerHeader_doesNotCallParse() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(jwtProvider, never()).parse(any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("ADMIN role 토큰 → ROLE_ADMIN authority 설정")
    void doFilterInternal_adminRoleToken_setsAdminAuthority() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer admin.jwt.token");
        when(jwtProvider.parse("admin.jwt.token")).thenReturn(new ParsedClaims(99L, "ADMIN"));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }
}
