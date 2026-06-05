package com.fandrops.user.infrastructure.auth;

import com.fandrops.user.application.exception.InvalidTokenException;
import com.fandrops.user.application.port.JwtProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            try {
                var claims = jwtProvider.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        claims.fanId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (InvalidTokenException e) {
                log.debug("유효하지 않은 Bearer 토큰: {}", e.getMessage());
                // SecurityContext 미설정 → Security가 401 처리
            }
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
