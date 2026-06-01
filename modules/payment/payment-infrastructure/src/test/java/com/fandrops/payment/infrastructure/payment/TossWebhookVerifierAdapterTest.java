package com.fandrops.payment.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fandrops.payment.infrastructure.config.TossProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TossWebhookVerifierAdapterTest {

    private static final String SECRET = "test-secret-key";
    private static final String RAW_BODY = "{\"eventType\":\"PAYMENT_STATUS_CHANGED\"}";

    @Mock
    TossProperties tossProperties;

    TossWebhookVerifierAdapter sut;

    @BeforeEach
    void setUp() {
        when(tossProperties.getSecretKey()).thenReturn(SECRET);
        sut = new TossWebhookVerifierAdapter(tossProperties);
    }

    @Test
    @DisplayName("유효한 서명 헤더 — verify 는 true 를 반환한다")
    void verify_valid_signature_returns_true() throws Exception {
        String signature = "sha256=" + computeHmac(SECRET, RAW_BODY);
        assertThat(sut.verify(RAW_BODY, signature)).isTrue();
    }

    @Test
    @DisplayName("변조된 서명 헤더 — verify 는 false 를 반환한다")
    void verify_tampered_signature_returns_false() {
        String tampered = "sha256=" + "0".repeat(64);
        assertThat(sut.verify(RAW_BODY, tampered)).isFalse();
    }

    @Test
    @DisplayName("null 서명 헤더 — verify 는 false 를 반환한다")
    void verify_null_header_returns_false() {
        assertThat(sut.verify(RAW_BODY, null)).isFalse();
    }

    @Test
    @DisplayName("sha256= 접두사 없는 헤더 — verify 는 false 를 반환한다")
    void verify_missing_prefix_returns_false() {
        assertThat(sut.verify(RAW_BODY, "invalidsignature")).isFalse();
    }

    private static String computeHmac(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}