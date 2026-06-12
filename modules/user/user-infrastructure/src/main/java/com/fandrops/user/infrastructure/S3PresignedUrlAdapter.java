package com.fandrops.user.infrastructure;

import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Component
public class S3PresignedUrlAdapter implements S3PresignedUrlPort {

    private final S3Presigner presigner;
    private final S3Properties properties;

    public S3PresignedUrlAdapter(S3Presigner presigner, S3Properties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUploadResult generate(String contentType) {
        String objectKey = properties.getUploadPrefix() + "/" + UUID.randomUUID() + extensionFor(contentType);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.getPresignedUrlExpiryMinutes()))
                .putObjectRequest(putRequest)
                .build();

        String presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
        String imageUrl = "https://%s.s3.%s.amazonaws.com/%s"
                .formatted(properties.getBucket(), properties.getRegion(), objectKey);

        return new PresignedUploadResult(presignedUrl, imageUrl);
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported content type: " + contentType);
        };
    }
}
