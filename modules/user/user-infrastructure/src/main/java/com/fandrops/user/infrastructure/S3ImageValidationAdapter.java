package com.fandrops.user.infrastructure;

import com.fandrops.user.application.exception.S3OperationException;
import com.fandrops.user.application.port.S3ImageValidationPort;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
public class S3ImageValidationAdapter implements S3ImageValidationPort {

    private final S3Client s3Client;
    private final S3Properties properties;

    public S3ImageValidationAdapter(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public boolean imageExists(String imageUrl) {
        String baseUrl = "https://%s.s3.%s.amazonaws.com/".formatted(
                properties.getBucket(), properties.getRegion());
        if (!imageUrl.startsWith(baseUrl)) {
            return true; // 우리 S3 버킷이 아니면 검증 skip
        }
        String key = imageUrl.substring(baseUrl.length());
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (SdkClientException e) {
            throw new S3OperationException("S3 이미지 존재 확인 실패", e);
        }
    }
}
