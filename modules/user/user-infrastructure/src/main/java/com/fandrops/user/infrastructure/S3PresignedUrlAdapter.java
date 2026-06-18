package com.fandrops.user.infrastructure;

import com.fandrops.user.application.constant.AllowedImageContentType;
import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.S3OperationException;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
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
    public PresignedUploadResult generate(String contentType, long contentLength) {
        return generate(contentType, contentLength, properties.getUploadPrefix());
    }

    @Override
    public PresignedUploadResult generateForProfileImage(String contentType, long contentLength) {
        return generate(contentType, contentLength, properties.getUploadProfileImagePrefix());
    }

    private PresignedUploadResult generate(String contentType, long contentLength, String prefix) {
        String normalizedPrefix = prefix.replaceAll("/+$", "");
        String objectKey = normalizedPrefix + "/" + UUID.randomUUID() + AllowedImageContentType.extensionFor(contentType);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(properties.getPresignedUrlExpiryMinutes()))
                .putObjectRequest(putRequest)
                .build();

        String presignedUrl;
        try {
            presignedUrl = presigner.presignPutObject(presignRequest).url().toString();
        } catch (S3Exception e) {
            throw new S3OperationException("S3 Presigned URL 생성 실패 (S3 응답 오류 " + e.statusCode() + ")", e);
        } catch (SdkClientException e) {
            throw new S3OperationException("S3 Presigned URL 생성 실패 (네트워크/자격증명 오류)", e);
        }

        String imageUrl = "https://%s.s3.%s.amazonaws.com/%s"
                .formatted(properties.getBucket(), properties.getRegion(), objectKey);
        Instant expiresAt = Instant.now().plusSeconds(properties.getPresignedUrlExpiryMinutes() * 60L);

        return new PresignedUploadResult(presignedUrl, imageUrl, expiresAt);
    }
}
