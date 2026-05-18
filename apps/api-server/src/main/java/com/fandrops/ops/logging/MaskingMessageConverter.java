package com.fandrops.ops.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * 민감 필드 값 마스킹 (password, token, authorization, secret 등).
 */
public class MaskingMessageConverter extends MessageConverter {

    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)((?:password|token|authorization|secret|api[_-]?key)\\s*[:=])\\s*[^\\s,}]+"
    );

    private static final String MASK = "$1****";

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isEmpty()) {
            return message;
        }
        return SENSITIVE_VALUE.matcher(message).replaceAll(MASK);
    }
}
