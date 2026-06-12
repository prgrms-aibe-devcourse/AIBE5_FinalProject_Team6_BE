package com.fandrops.user.application.service;

import com.fandrops.user.application.dto.PresignedUploadResult;
import com.fandrops.user.application.exception.InvalidContentTypeException;
import com.fandrops.user.application.port.S3PresignedUrlPort;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class UploadService {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final S3PresignedUrlPort s3PresignedUrlPort;

    public UploadService(S3PresignedUrlPort s3PresignedUrlPort) {
        this.s3PresignedUrlPort = s3PresignedUrlPort;
    }

    public PresignedUploadResult requestPresignedUrl(String contentType) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidContentTypeException(contentType);
        }
        return s3PresignedUrlPort.generate(contentType);
    }
}
