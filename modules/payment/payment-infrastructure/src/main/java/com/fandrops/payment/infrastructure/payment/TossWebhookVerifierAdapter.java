package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.application.payment.TossWebhookVerifyPort;
import com.fandrops.payment.infrastructure.config.TossProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TossWebhookVerifierAdapter implements TossWebhookVerifyPort {

    private static final Logger log = LoggerFactory.getLogger(TossWebhookVerifierAdapter.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final TossProperties tossProperties;

    public TossWebhookVerifierAdapter(TossProperties tossProperties) {
        this.tossProperties = tossProperties;
    }

    @Override
    public boolean verify(String rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            log.warn("웹훅 서명 헤더 형식 오류: header={}", signatureHeader);
            return false;
        }
        String receivedHex = signatureHeader.substring("sha256=".length());
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    tossProperties.getSecretKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(receivedHex);
            return MessageDigest.isEqual(computed, received);
        } catch (Exception e) {
            log.error("웹훅 서명 검증 중 오류 발생", e);
            return false;
        }
    }
}