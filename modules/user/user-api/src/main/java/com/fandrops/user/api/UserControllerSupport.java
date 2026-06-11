package com.fandrops.user.api;

import com.fandrops.user.api.dto.AuthTokenResponse;
import com.fandrops.user.application.dto.AuthTokenResult;
import com.fandrops.user.application.exception.InvalidCredentialsException;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.UUID;

public abstract class UserControllerSupport {

    private final Environment environment;

    protected UserControllerSupport(Environment environment) {
        this.environment = environment;
    }

    protected boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    protected static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }

    protected AuthTokenResponse toAuthTokenResponse(AuthTokenResult result) {
        return new AuthTokenResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }

    protected Long resolveFanId(Authentication authentication, Long localFanIdHeader) {
        if (localFanIdHeader != null && isLocalProfile()) {
            return localFanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long fanId) {
            return fanId;
        }
        throw new InvalidCredentialsException("인증이 필요합니다.");
    }

    protected Long resolveAdminId(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long adminId) {
            return adminId;
        }
        throw new InvalidCredentialsException("인증이 필요합니다.");
    }
}
