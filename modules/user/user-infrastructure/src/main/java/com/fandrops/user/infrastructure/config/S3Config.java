package com.fandrops.user.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties properties) {
        // 자격증명(신분증)은 AWS SDK Default Chain이 자동 탐색:
        // 1순위 환경변수(AWS_ACCESS_KEY_ID) → 2순위 ~/.aws/credentials → 3순위 EC2 IAM Role
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .build();
    }
}
