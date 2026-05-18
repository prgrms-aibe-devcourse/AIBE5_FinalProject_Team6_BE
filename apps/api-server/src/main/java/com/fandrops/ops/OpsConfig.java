package com.fandrops.ops;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OpsProperties.class, FeatureFlagsProperties.class})
public class OpsConfig {

    @PostConstruct
    void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
