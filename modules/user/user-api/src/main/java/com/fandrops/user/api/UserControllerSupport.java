package com.fandrops.user.api;

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

    protected Long resolveFanId(Authentication authentication, Long localFanIdHeader) {
        if (localFanIdHeader != null && isLocalProfile()) {
            return localFanIdHeader;
        }
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long fanId) {
            return fanId;
        }
        throw new IllegalStateException("인증 정보가 없습니다.");
    }
}
