package com.fandrops.user.infrastructure.auth;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
class RedisStoreUtils {

    private RedisStoreUtils() {}

    static Optional<Long> parseFanId(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            String preview = value.length() > 4 ? value.substring(0, 4) + "..." : value;
            log.warn("Invalid fanId payload in Redis: '{}'", preview);
            return Optional.empty();
        }
    }
}
