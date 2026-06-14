package com.fandrops.user.application.port;

import com.fandrops.user.application.dto.PresignedUploadResult;

public interface S3PresignedUrlPort {
    PresignedUploadResult generate(String contentType, long contentLength);
}