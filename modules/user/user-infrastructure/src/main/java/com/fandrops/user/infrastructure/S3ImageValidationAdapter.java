package com.fandrops.user.infrastructure;

import com.fandrops.user.application.exception.S3OperationException;
import com.fandrops.user.application.port.S3ImageValidationPort;
import com.fandrops.user.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

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
            return false; // 우리 버킷 외 URL은 유효하지 않은 배너 이미지 URL로 간주
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
        } catch (S3Exception e) {
            throw new S3OperationException("S3 이미지 확인 실패 (S3 응답 오류 " + e.statusCode() + ")", e);
        } catch (SdkClientException e) {
            throw new S3OperationException("S3 이미지 확인 실패 (네트워크/자격증명 오류)", e);
        }
    }
}
