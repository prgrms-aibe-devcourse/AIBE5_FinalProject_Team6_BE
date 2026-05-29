package com.fandrops.payment.infrastructure.payment;

import com.fandrops.payment.application.payment.TossAuthenticationException;
import com.fandrops.payment.application.payment.TossConfirmResult;
import com.fandrops.payment.application.payment.TossPaymentPort;
import com.fandrops.payment.application.payment.TossPaymentUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
class TossPaymentGatewayAdapter implements TossPaymentPort {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentGatewayAdapter.class);
    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    TossPaymentGatewayAdapter(RestClient tossRestClient, ObjectMapper objectMapper) {
        this.tossRestClient = tossRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public TossConfirmResult confirm(String tossPaymentKey, long amount, Long orderId) {
        TossConfirmBody body = new TossConfirmBody(tossPaymentKey, amount, String.valueOf(orderId));
        try {
            TossSuccessBody response = tossRestClient.post()
                    .uri(CONFIRM_PATH)
                    .body(body)
                    .retrieve()
                    .body(TossSuccessBody.class);

            if (response == null || response.approvedAt() == null) {
                throw new TossPaymentUnavailableException("Toss PG 응답 파싱 실패: 빈 응답");
            }
            Instant approvedAt = OffsetDateTime.parse(response.approvedAt()).toInstant();
            return TossConfirmResult.success(response.method(), approvedAt);

        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                log.error("Toss PG 인증 오류(설정 확인 필요): status={}", status);
                throw new TossAuthenticationException("Toss PG 인증 오류: " + status);
            }
            if (e.getStatusCode().is5xxServerError()) {
                log.error("Toss PG 서버 오류(재시도 가능): status={}", status);
                throw new TossPaymentUnavailableException("Toss PG 일시 오류: " + status);
            }
            try {
                TossErrorBody error = objectMapper.readValue(e.getResponseBodyAsString(), TossErrorBody.class);
                log.warn("Toss PG confirm 실패: code={}, message={}", error.code(), error.message());
                return TossConfirmResult.failure(error.code(), error.message());
            } catch (Exception parseEx) {
                log.warn("Toss PG 오류 응답 파싱 실패: status={}", status, parseEx);
                return TossConfirmResult.failure("UNKNOWN_ERROR", e.getMessage());
            }
        }
    }

    record TossConfirmBody(String paymentKey, long amount, String orderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TossSuccessBody(String status, String method, String approvedAt) {}

    record TossErrorBody(String code, String message) {}
}