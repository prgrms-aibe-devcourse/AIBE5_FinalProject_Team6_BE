package com.fandrops.ops;

import io.lettuce.core.metrics.MicrometerCommandLatencyRecorder;
import io.lettuce.core.metrics.MicrometerOptions;
import io.lettuce.core.resource.ClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LettuceMetricsConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public ClientResources clientResources(MeterRegistry meterRegistry) {
        return ClientResources.builder()
                .commandLatencyRecorder(
                        new MicrometerCommandLatencyRecorder(meterRegistry, MicrometerOptions.create()))
                .build();
    }
}
