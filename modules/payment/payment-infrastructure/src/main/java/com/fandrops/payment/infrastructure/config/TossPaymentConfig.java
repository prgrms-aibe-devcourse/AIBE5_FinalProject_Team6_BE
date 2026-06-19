package com.fandrops.payment.infrastructure.config;

import com.fandrops.payment.application.payment.PaymentConfirmService;
import com.fandrops.payment.application.payment.PaymentQueryService;
import com.fandrops.payment.application.payment.PaymentTimeoutItemProcessor;
import com.fandrops.payment.application.payment.PaymentTimeoutService;
import com.fandrops.payment.application.payment.PaymentWebhookService;
import com.fandrops.payment.application.payment.TossPaymentPort;
import com.fandrops.payment.domain.payment.OrderFanQueryPort;
import com.fandrops.payment.domain.payment.PaymentRepository;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
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

    private final TossProperties tossProperties;
    private final Environment environment;

    public TossPaymentConfig(TossProperties tossProperties, Environment environment) {
        this.tossProperties = tossProperties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
        if (!isLocal && (tossProperties.getSecretKey() == null || tossProperties.getSecretKey().isBlank())) {
            throw new IllegalStateException("toss.api.secret-key가 설정되지 않았습니다");
        }
    }

    @Bean
    public RestClient tossRestClient() {
        String credentials = Base64.getEncoder().encodeToString(
                (tossProperties.getSecretKey() + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(tossProperties.getConnectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
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

    @Bean
    public PaymentWebhookService paymentWebhookService(PaymentRepository paymentRepository,
                                                        ApplicationEventPublisher eventPublisher) {
        return new PaymentWebhookService(paymentRepository, eventPublisher);
    }

    @Bean
    public PaymentQueryService paymentQueryService(PaymentRepository paymentRepository,
                                                   OrderFanQueryPort orderFanQueryPort) {
        return new PaymentQueryService(paymentRepository, orderFanQueryPort);
    }
}