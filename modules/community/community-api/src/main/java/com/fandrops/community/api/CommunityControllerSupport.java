package com.fandrops.community.api;

import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.UUID;

public abstract class CommunityControllerSupport {

    private final Environment environment;

    protected CommunityControllerSupport(Environment environment) {
        this.environment = environment;
    }

    protected boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }

    protected static String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : UUID.randomUUID().toString();
    }

    protected Principal resolvePrincipals(Authentication authentication,
                                          Long fanIdHeader, Long artistMemberIdHeader) {
        if (fanIdHeader != null && isLocalProfile()) {
            return Principal.ofFan(fanIdHeader);
        }
        if (artistMemberIdHeader != null && isLocalProfile()) {
            return Principal.ofArtistMember(artistMemberIdHeader);
        }
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return Principal.ofFan(Long.parseLong(authentication.getName()));
        }
        throw new IllegalArgumentException("인증 정보가 없습니다. Bearer 토큰을 제공하세요.");
    }
}