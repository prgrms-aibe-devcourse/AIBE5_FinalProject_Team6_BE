package com.fandrops.payment.infrastructure.config;

import com.fandrops.payment.application.payment.PaymentConfirmService;
import com.fandrops.payment.application.payment.PaymentTimeoutItemProcessor;
import com.fandrops.payment.application.payment.PaymentTimeoutService;
import com.fandrops.payment.application.payment.TossPaymentPort;
import com.fandrops.payment.domain.payment.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TossProperties.class)
public class TossPaymentConfig {

    @Bean
    public RestClient tossRestClient(TossProperties tossProperties) {
        String credentials = Base64.getEncoder().encodeToString(
                (tossProperties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(tossProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(tossProperties.getReadTimeout());

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(tossProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public PaymentConfirmService paymentConfirmService(PaymentRepository paymentRepository,
                                                       TossPaymentPort tossPaymentPort,
                                                       ApplicationEventPublisher eventPublisher) {
        return new PaymentConfirmService(paymentRepository, tossPaymentPort, eventPublisher);
    }

    @Bean
    public PaymentTimeoutItemProcessor paymentTimeoutItemProcessor(PaymentRepository paymentRepository,
                                                                    ApplicationEventPublisher eventPublisher) {
        return new PaymentTimeoutItemProcessor(paymentRepository, eventPublisher);
    }

    @Bean
    public PaymentTimeoutService paymentTimeoutService(PaymentRepository paymentRepository,
                                                       PaymentTimeoutItemProcessor paymentTimeoutItemProcessor) {
        return new PaymentTimeoutService(paymentRepository, paymentTimeoutItemProcessor);
    }
}